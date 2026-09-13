package com.voltic.app.chain

import io.github.adraffy.ens.ENSNormalize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.jcajce.provider.digest.Keccak
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Thrown for any ENS-specific failure: invalid/unregistered name, no resolver set,
 * malformed reverse record, etc. Distinct from network/IO exceptions so callers
 * can show a clean "Invalid ENS name" message instead of a generic error.
 */
class EnsResolutionException(message: String) : Exception(message)

object EnsResolver {

    // ENS lives on Ethereum L1 regardless of which chain Voltic transacts on —
    // always resolves through ChainConfig.current.ethereumRpcs, never the arbitrumRpcs list.
    private const val ENS_REGISTRY = "0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e"
    private const val ZERO_ADDRESS = "0x0000000000000000000000000000000000000000"

    private val ADDR_REVERSE_NODE = hexToBytes(
        "91d1777781884d03a6757a803996e38de2a42967fb37eeaca72729271025a9e2"
    )

    // ---------- hashing ----------

    private fun keccak256(input: ByteArray): ByteArray =
        Keccak.Digest256().digest(input)

    private fun namehash(name: String): ByteArray {
        var node = ByteArray(32)
        if (name.isEmpty()) return node
        val labels = name.split(".")
        for (i in labels.indices.reversed()) {
            val labelHash = keccak256(labels[i].toByteArray(Charsets.UTF_8))
            node = keccak256(node + labelHash)
        }
        return node
    }

    private fun sha3HexAddress(address: String): ByteArray {
        val hexAscii = address.removePrefix("0x").lowercase()
        return keccak256(hexAscii.toByteArray(Charsets.UTF_8))
    }

    private fun reverseNode(address: String): ByteArray =
        keccak256(ADDR_REVERSE_NODE + sha3HexAddress(address))

    // ---------- byte/hex helpers ----------

    private fun hexToBytes(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun hex(bytes: ByteArray): String =
        "0x" + bytes.joinToString("") { "%02x".format(it) }

    private fun addressFromWord(hex32: String): String =
        "0x" + hex32.removePrefix("0x").takeLast(40)

    // ---------- raw JSON-RPC ----------

    /**
     * Tries each mainnet RPC in ChainConfig.current.ethereumRpcs in order, falling
     * through to the next on any failure (timeout, non-2xx, RPC-level error).
     * Throws only if every endpoint in the list fails.
     */
    private fun ethCall(to: String, data: String): String {
        val rpcs = ChainConfig.current.ethereumRpcs
        require(rpcs.isNotEmpty()) { "No ethereumRpcs configured for ${ChainConfig.current.chainName}" }

        var lastError: Exception? = null
        for (rpcUrl in rpcs) {
            try {
                return performEthCall(rpcUrl, to, data)
            } catch (e: Exception) {
                lastError = e
                // fall through to next RPC in the list
            }
        }
        throw EnsResolutionException(
            "All mainnet RPCs failed for ${ChainConfig.current.chainName}: ${lastError?.message}"
        )
    }

    private fun performEthCall(rpcUrl: String, to: String, data: String): String {
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "eth_call")
            put(
                "params",
                JSONArray().apply {
                    put(JSONObject().apply {
                        put("to", to)
                        put("data", data)
                    })
                    put("latest")
                }
            )
        }

        val conn = (URL(rpcUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Content-Type", "application/json")
        }

        conn.outputStream.use { it.write(payload.toString().toByteArray()) }

        val responseCode = conn.responseCode
        val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
        val responseText = stream.bufferedReader().use { it.readText() }

        if (responseCode !in 200..299) {
            throw EnsResolutionException("RPC HTTP $responseCode from $rpcUrl")
        }

        val json = JSONObject(responseText)
        if (json.has("error")) {
            throw EnsResolutionException(
                "RPC error from $rpcUrl: ${json.getJSONObject("error").optString("message")}"
            )
        }

        return json.getString("result")
    }

    // ---------- normalization ----------

    private fun normalize(rawName: String): String {
        val trimmed = rawName.trim()
        require(trimmed.endsWith(".eth", ignoreCase = true)) { "Not an ENS name" }
        return try {
            // Reference ENSIP-15 implementation — handles confusables, emoji,
            // bidi, punycode, case folding, etc. Do not hand-roll this.
            ENSNormalize.ENSIP15.normalize(trimmed)
        } catch (e: Exception) {
            throw EnsResolutionException("Invalid ENS name: ${e.message}")
        }
    }

    // ---------- public API ----------

    /**
     * Resolves a .eth name to an address. Throws [EnsResolutionException] if the
     * name is malformed, unregistered, has no resolver, or has no address set.
     */
    suspend fun resolve(rawName: String): String = withContext(Dispatchers.IO) {
        val name = normalize(rawName)
        val nodeHex = hex(namehash(name)).removePrefix("0x")

        // resolver(bytes32)
        val resolverAddr = addressFromWord(ethCall(ENS_REGISTRY, "0x0178b8bf$nodeHex"))
        if (resolverAddr.equals(ZERO_ADDRESS, ignoreCase = true)) {
            throw EnsResolutionException("No resolver set for $name")
        }

        // addr(bytes32)
        val address = addressFromWord(ethCall(resolverAddr, "0x3b3b57de$nodeHex"))
        if (address.equals(ZERO_ADDRESS, ignoreCase = true)) {
            throw EnsResolutionException("$name is not registered to an address")
        }

        address
    }

    /**
     * Reverse-resolves an address to its primary ENS name, then forward-verifies
     * the result actually points back to the same address (reverse records can be
     * set by anyone and aren't proof of ownership on their own).
     * Returns null if there's no reverse record, no forward match, or any lookup fails.
     */
    suspend fun getVerifiedReverseRecord(address: String): String? = withContext(Dispatchers.IO) {
        try {
            val node = reverseNode(address)
            val nodeHex = hex(node).removePrefix("0x")

            val resolverAddr = addressFromWord(ethCall(ENS_REGISTRY, "0x0178b8bf$nodeHex"))
            if (resolverAddr.equals(ZERO_ADDRESS, ignoreCase = true)) return@withContext null

            // name(bytes32) — dynamic string return: [offset][length][data]
            val raw = ethCall(resolverAddr, "0x691f3431$nodeHex").removePrefix("0x")
            if (raw.length < 128) return@withContext null

            val length = raw.substring(64, 128).toInt(16)
            if (length == 0) return@withContext null

            val dataHex = raw.substring(128, 128 + length * 2)
            val name = dataHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                .toString(Charsets.UTF_8)

            // Forward-verify: resolve the claimed name and check it matches the address.
            val forwardAddress = resolve(name)
            if (!forwardAddress.equals(address, ignoreCase = true)) return@withContext null

            name
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Convenience for send-flow: returns the resolved address for a .eth name,
     * or the input unchanged if it's already a raw address.
     * Throws [EnsResolutionException] on any ENS resolution failure — callers
     * must catch this and surface it before allowing the user to confirm a send.
     */
    suspend fun getReceiverAddress(rawRecipient: String): String {
        val trimmed = rawRecipient.trim()
        if (trimmed.endsWith(".eth", ignoreCase = true)) {
            return resolve(trimmed)
        }
        return trimmed
    }
}