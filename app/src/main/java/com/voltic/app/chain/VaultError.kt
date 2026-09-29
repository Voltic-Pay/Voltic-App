package com.voltic.app.chain

import com.voltic.contracts.VolticSmartWallet
import io.ethers.abi.call.ReadContractCall
import io.ethers.abi.error.ExecutionRevertedError
import io.ethers.abi.error.PanicError
import io.ethers.abi.error.RevertError
import io.ethers.core.Result
import io.ethers.core.ThrowableError
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId

/**
 * Simulates the call via eth_call BEFORE we sign/broadcast anything. If the contract would
 * revert, throws the decoded ContractError (as a ThrowableError.Exception) so the UI can show
 * a real message and the user doesn't burn gas on a doomed tx.
 */
suspend fun <C, B : ReadContractCall<C, B>> ReadContractCall<C, B>.assertWillSucceed(from: Address) {
    this.from = from
    when (val result = this.call(BlockId.LATEST).sendAwait()) {
        is Result.Failure -> throw result.error.toException()
        is Result.Success -> Unit
    }
}

object VaultErrors {

    /** Walks the cause chain looking for a ThrowableError and returns a friendly message, or null. */
    fun describe(t: Throwable): String? {
        var cur: Throwable? = t
        while (cur != null) {
            if (cur is ThrowableError.Exception) return message(cur.error)
            cur = cur.cause
        }
        return null
    }

    private fun message(error: ThrowableError): String? = when (error) {
        is VolticSmartWallet.ZeroAmount -> "Amount can't be zero."
        is VolticSmartWallet.ZeroAddress -> "Recipient address can't be empty."
        is VolticSmartWallet.InvalidToAddress -> "That recipient address isn't valid."
        is VolticSmartWallet.InsufficientBalance -> "Not enough balance in the vault for this."
        is VolticSmartWallet.WalletDisabled -> "This wallet has been disabled by its owner."
        is VolticSmartWallet.ExpiredDeadline -> "This payment request expired — try again."
        is VolticSmartWallet.DeadlineTooFarInFuture -> "Payment deadline is too far in the future."
        is VolticSmartWallet.NonceAlreadyUsed -> "This payment was already processed."
        is VolticSmartWallet.InvalidSignature,
        is VolticSmartWallet.ECDSAInvalidSignature,
        is VolticSmartWallet.ECDSAInvalidSignatureLength,
        is VolticSmartWallet.ECDSAInvalidSignatureS -> "Payment signature didn't verify — try again."
        is VolticSmartWallet.SpendLimitExceeded -> "This would go over your spending limit for this period."
        is VolticSmartWallet.ReentrancyGuardReentrantCall -> "Another vault operation is already in progress."
        is RevertError -> error.reason
        is PanicError -> "Vault contract error (panic: ${error.kind.name})."
        is ExecutionRevertedError -> "The vault rejected this transaction."
        else -> null // RPC/network errors etc. fall through to the raw message
    }
}