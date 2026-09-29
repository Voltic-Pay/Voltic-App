package com.voltic.app.wallet

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences // TODO: this is a deprecated lib
import androidx.security.crypto.MasterKey // TODO: this is a deprecated lib
import io.ethers.core.FastHex
import io.ethers.crypto.bip39.MnemonicCode
import io.ethers.signers.MnemonicKeySource
import io.ethers.signers.PrivateKeySigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStoreException
import java.security.ProviderException

/**
 * Exception thrown when wallet operations fail.
 * Provides context-specific error information for debugging.
 */
sealed class WalletException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class KeystoreInitializationFailed(cause: Throwable) :
        WalletException("Failed to initialize Android Keystore. Device may not support hardware security.", cause)

    class KeyDecryptionFailed(cause: Throwable) :
        WalletException("Failed to decrypt wallet data. Keystore operation failed.", cause)

    class WalletGenerationFailed(cause: Throwable) :
        WalletException("Failed to generate secp256k1 key pair.", cause)

    class InvalidPrivateKeyFormat(message: String) :
        WalletException(message)

    class NoMnemonicAvailable :
        WalletException("No recovery phrase is stored for this wallet.")
}

/**
 * Secure wallet manager using ethers-kt's secp256k1 implementation, with a
 * BIP-39 (24-word) recovery phrase as the source of the key — not a raw
 * random key. This is the accessibility/recovery layer: if the phone is
 * lost, wiped, or the app is reinstalled, the wallet can be restored by
 * re-entering the same 24 words.
 *
 * All Keystore operations MUST run on background threads — use the
 * *Async variants from UI code.
 *
 * ROADMAP (not urgent): EncryptedSharedPreferences was deprecated in
 * security-crypto 1.1.0-alpha07 in favor of DataStore + Tink. Fine to keep
 * using it for now — migrate post-hackathon if this becomes a real product.
 *
 * SECURITY NOTE: the mnemonic is stored encrypted (same Keystore-backed
 * encryption as the private key), so it can be shown to the user once on
 * a dedicated "back up your wallet" screen. It is never logged, and there
 * is no bulk "export everything as plaintext" API — the only way to see
 * the phrase is the explicit getMnemonicForBackup() call, meant to be
 * gated behind a deliberate user action (e.g. a confirmation dialog),
 * not called incidentally.
 */
@Suppress("DEPRECATION") // EncryptedSharedPreferences deprecated but fine for now — see ROADMAP note above
class WalletManager(private val context: Context) {

    private val prefsName = "loosen_wallet_prefs"
    private val privateKeyField = "encrypted_private_key"
    private val addressField = "ethereum_address"
    private val mnemonicField = "encrypted_mnemonic"
    private val balanceHiddenField = "balance_hidden"

    companion object {
        private const val TAG = "WalletManager"
        private const val PRIVATE_KEY_HEX_LENGTH = 64 // 32 bytes = 256 bits
        private const val ENTROPY_BITS = 256 // -> 24 words


    }

    /** Get or create the MasterKey for Android Keystore encryption. */
    private fun getMasterKey(): MasterKey {
        return try {
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        } catch (e: InvalidAlgorithmParameterException) {
            Log.e(TAG, "Invalid algorithm parameter for Keystore", e)
            throw WalletException.KeystoreInitializationFailed(e)
        } catch (e: KeyStoreException) {
            Log.e(TAG, "Keystore exception during initialization", e)
            throw WalletException.KeystoreInitializationFailed(e)
        } catch (e: ProviderException) {
            Log.e(TAG, "Provider exception - device may not support StrongBox", e)
            throw WalletException.KeystoreInitializationFailed(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error initializing Keystore", e)
            throw WalletException.KeystoreInitializationFailed(e)
        }
    }

    /** Get the EncryptedSharedPreferences instance, backed by Android Keystore. */
    private fun getEncryptedPrefs(): EncryptedSharedPreferences {
        return try {
            EncryptedSharedPreferences.create(
                context,
                prefsName,
                getMasterKey(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ) as EncryptedSharedPreferences
        } catch (e: WalletException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences", e)
            throw WalletException.KeystoreInitializationFailed(e)
        }
    }

    /**
     * Derive a [PrivateKeySigner] from a BIP-39 mnemonic using the standard
     * Ethereum derivation path (m/44'/60'/0'/0/0), account index 0.
     */
    private fun signerFromMnemonic(mnemonic: MnemonicCode): PrivateKeySigner {
        return MnemonicKeySource(mnemonic).getAccount(0)
    }

    /** Render a [MnemonicCode] back to its space-separated word phrase. */
    private fun MnemonicCode.toPhrase(): String = words.joinToString(" ")

    /**
     * Check if a wallet exists. Distinguishes "no wallet" (false) from
     * "wallet exists but couldn't be read" (throws) — important so callers
     * never accidentally treat a broken Keystore as "first launch" and
     * silently generate a second wallet on top of an existing one.
     *
     * WARNING: Keystore I/O — call from a background thread.
     */
    @Throws(WalletException::class)
    fun hasExistingWallet(): Boolean {
        return try {
            val prefs = getEncryptedPrefs()
            prefs.contains(privateKeyField).also {
                Log.d(TAG, "Wallet existence check: $it")
            }
        } catch (e: WalletException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error checking wallet existence", e)
            throw WalletException.KeyDecryptionFailed(e)
        }
    }

    /**
     * Generate a new 24-word BIP-39 mnemonic, derive a wallet from it, and
     * store both (encrypted) locally.
     *
     * @return the generated PrivateKeySigner AND the mnemonic, so the caller
     *         can show the 24 words to the user immediately for backup. This
     *         is the only place the mnemonic is returned in plaintext — do
     *         not log or persist it anywhere else.
     *
     * WARNING: Keystore I/O — call from a background thread.
     */
    @Throws(WalletException::class)
    fun createNewWallet(): Pair<PrivateKeySigner, String> {
        return try {
            // fromRandomEntropy sources its own secure randomness internally.
            val mnemonicCode = MnemonicCode.fromRandomEntropy(ENTROPY_BITS)
            val mnemonic = mnemonicCode.toPhrase()

            val signer = signerFromMnemonic(mnemonicCode)

            // Fixed-width hex (64 chars = 32 bytes), so short keys with
            // leading zero bytes round-trip correctly.
            val privateKeyHex = FastHex.encodeWithoutPrefix(signer.signingKey.privateKey)
            val address = signer.address.toString()

            Log.d(TAG, "Generated new wallet: $address")

            val prefs = getEncryptedPrefs()
            prefs.edit()
                .putString(privateKeyField, privateKeyHex)
                .putString(addressField, address)
                .putString(mnemonicField, mnemonic)
                .apply()

            Log.d(TAG, "Wallet stored successfully")
            signer to mnemonic
        } catch (e: WalletException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error creating wallet", e)
            throw WalletException.WalletGenerationFailed(e)
        }
    }

    /**
     * Restore a wallet from a previously backed-up 24-word mnemonic
     * (e.g. user reinstalled the app, or is setting up a new phone).
     *
     * @throws WalletException.InvalidPrivateKeyFormat if the phrase is not
     *         a valid BIP-39 mnemonic.
     *
     * WARNING: Keystore I/O — call from a background thread.
     */
    @Throws(WalletException::class)
    fun restoreWalletFromMnemonic(mnemonic: String): PrivateKeySigner {

        fun cleanAndNormalizeMnemonic(rawInput: String): String {
            return rawInput
                .lowercase()
                // Replace EVERYTHING that is not 'a'…'z' numbers, weird Unicode, \n, \t, with a space
                .map { char -> if (char in 'a'..'z') char else ' ' }
                .joinToString("")
                .asSequence()
                .filter { it.isLetter() || it == ' ' }
                .joinToString("")
                .split(' ')
                .filter { it.isNotEmpty() }
                .joinToString(" ")
        }
        val trimmed = cleanAndNormalizeMnemonic(mnemonic)

        // MnemonicCode's constructor validates word count + checksum itself
        // (throws IllegalArgumentException) — this replaces the old
        // MnemonicUtils.validateMnemonic(...) boolean check.
        val mnemonicCode = try {
            MnemonicCode(trimmed)
        } catch (e: RuntimeException) {
            throw WalletException.InvalidPrivateKeyFormat(
                "That recovery phrase isn't valid. Check the spelling and word order."
            )
        }

        return try {
            val signer = signerFromMnemonic(mnemonicCode)
            val privateKeyHex = FastHex.encodeWithoutPrefix(signer.signingKey.privateKey)

            val prefs = getEncryptedPrefs()
            prefs.edit()
                .putString(privateKeyField, privateKeyHex)
                .putString(addressField, signer.address.toString())
                .putString(mnemonicField, trimmed)
                .apply()

            Log.d(TAG, "Wallet restored from mnemonic: ${signer.address}")
            signer
        } catch (e: WalletException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring wallet from mnemonic", e)
            throw WalletException.WalletGenerationFailed(e)
        }
    }

    /**
     * Retrieve the stored mnemonic for an explicit "view my backup phrase"
     * screen. Only call this from a deliberate, user-initiated action —
     * never on app launch or incidentally.
     *
     * WARNING: Keystore I/O — call from a background thread.
     */
    @Throws(WalletException::class)
    fun getMnemonicForBackup(): String {
        val prefs = getEncryptedPrefs()
        return prefs.getString(mnemonicField, null)
            ?: throw WalletException.NoMnemonicAvailable()
    }

    /**
     * Load the existing wallet from encrypted storage.
     *
     * WARNING: Keystore I/O — call from a background thread.
     *
     * @return PrivateKeySigner, or null only if no wallet has been created yet.
     */
    @Throws(WalletException::class)
    fun loadExistingWallet(): PrivateKeySigner? {
        return try {
            val prefs = getEncryptedPrefs()
            val privateKeyHex = prefs.getString(privateKeyField, null)
                ?: run {
                    Log.w(TAG, "No wallet found in storage")
                    return null
                }

            if (privateKeyHex.length != PRIVATE_KEY_HEX_LENGTH) {
                throw WalletException.InvalidPrivateKeyFormat(
                    "Invalid private key format: expected $PRIVATE_KEY_HEX_LENGTH hex chars, got ${privateKeyHex.length}"
                )
            }

            // PrivateKeySigner(String) validates hex format + 32-byte length
            // internally and throws IllegalArgumentException on bad input —
            // replaces the old manual BigInteger + Sign.publicKeyFromPrivate path.
            val signer = PrivateKeySigner(privateKeyHex)
            Log.d(TAG, "Wallet loaded successfully: ${signer.address}")
            signer
        } catch (e: WalletException) {
            throw e
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid hex format in stored private key", e)
            throw WalletException.InvalidPrivateKeyFormat("Private key is not valid hexadecimal")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading wallet", e)
            throw WalletException.KeyDecryptionFailed(e)
        }
    }

    /** Async wrapper for wallet creation — safe to call from the main thread. */
    suspend fun createNewWalletAsync(): Pair<PrivateKeySigner, String> = withContext(Dispatchers.Default) {
        createNewWallet()
    }

    /** Async wrapper for mnemonic restore — safe to call from the main thread. */
    suspend fun restoreWalletFromMnemonicAsync(mnemonic: String): PrivateKeySigner = withContext(Dispatchers.Default) {
        restoreWalletFromMnemonic(mnemonic)
    }

    /**
     * Async wrapper for wallet loading — safe to call from the main thread.
     * Swallows WalletException down to null + a warning log, since most
     * call sites just want "do I have a wallet to show, yes or no."
     * Use loadExistingWallet() directly if you need to distinguish
     * "no wallet" from "wallet is broken."
     */
    suspend fun loadExistingWalletAsync(): PrivateKeySigner? = withContext(Dispatchers.Default) {
        try {
            loadExistingWallet()
        } catch (e: WalletException) {
            Log.w(TAG, "Failed to load wallet: ${e.message}", e)
            null
        }
    }

    /** Async wrapper for retrieving the backup phrase — safe to call from the main thread. TODO: don't for get this */
    suspend fun getMnemonicForBackupAsync(): String = withContext(Dispatchers.Default) {
        getMnemonicForBackup()
    }

    /** Check if the balance should be hidden. */
    fun isBalanceHidden(): Boolean {
        return getEncryptedPrefs().getBoolean(balanceHiddenField, false)
    }

    /** Update the balance visibility preference. */
    fun setBalanceHidden(hidden: Boolean) {
        getEncryptedPrefs().edit().putBoolean(balanceHiddenField, hidden).apply()
    }
}