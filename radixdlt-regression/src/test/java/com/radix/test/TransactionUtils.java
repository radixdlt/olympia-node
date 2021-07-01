package com.radix.test;

import com.radix.test.account.Account;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.client.lib.api.ValidatorAddress;
import com.radixdlt.client.lib.dto.TransactionStatusDTO;
import com.radixdlt.client.lib.dto.TxDTO;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

public final class TransactionUtils {

    private TransactionUtils() {

    }

    public static TransactionRequest createTransferRequest(AccountAddress from, AccountAddress to, String tokenRri, UInt256 amount,
                                                           String message) {
        return Utils.isNullOrEmpty(message)
            ? TransactionRequest.createBuilder(from)
                .transfer(from, to, amount, tokenRri)
                .build()
            : TransactionRequest.createBuilder(from)
                .transfer(from, to, amount, tokenRri)
                .message(message)
                .build();
    }

    public static TransactionRequest createUnstakingRequest(AccountAddress from, ValidatorAddress unstakeFrom,
                                                            UInt256 stake) {
        return TransactionRequest.createBuilder(from)
            .unstake(from, unstakeFrom, stake)
            .build();
    }

    public static TransactionRequest createStakingRequest(AccountAddress from, ValidatorAddress to, UInt256 stake) {
        return TransactionRequest.createBuilder(from)
                .stake(from, to, stake)
                .build();
    }

    public static void performStaking(Account sender, ValidatorAddress to, UInt256 amount) {
        var request = createStakingRequest(sender.getAddress(), to, amount);
        performTransaction(sender, request);
    }

    public static TransactionStatusDTO performTxStatusRequest(Account account, AID txId) {
        return account.transaction().status(txId).fold(Utils::toTestFailureException,
            transactionStatusDTO -> transactionStatusDTO);
    }

    public static TxDTO performNativeTokenTransferAndFold(Account sender, Account receiver, int amount) {
        var request = TransactionUtils.createTransferRequest(sender.getAddress(), receiver.getAddress(),
                sender.getNativeToken().getRri(), Utils.fromMajorToMinor(amount), null);
        return performTransaction(sender, request).fold(Utils::toTestFailureException, txDTO -> txDTO);
    }

    public static Result<TxDTO> performNativeTokenTransfer(Account sender, Account receiver, int majorAmount) {
        var request = TransactionUtils.createTransferRequest(sender.getAddress(), receiver.getAddress(),
            sender.getNativeToken().getRri(), Utils.fromMajorToMinor(majorAmount), null);
        return performTransaction(sender, request);
    }

    public static Result<TxDTO> performNativeTokenTransfer(Account sender, Account receiver, int majorAmount, String message) {
        var request = TransactionUtils.createTransferRequest(sender.getAddress(), receiver.getAddress(),
                sender.getNativeToken().getRri(), Utils.fromMajorToMinor(majorAmount), message);
        return performTransaction(sender, request);
    }

    public static Result<TxDTO> performNativeTokenTransfer(Account sender, Account receiver, UInt256 amount, String message) {
        var request = TransactionUtils.createTransferRequest(sender.getAddress(), receiver.getAddress(),
                sender.getNativeToken().getRri(), amount, message);
        return performTransaction(sender, request);
    }

    public static Result<TxDTO> performTransaction(Account account, TransactionRequest request) {
        var keyPair = account.getKeyPair();
		return account.transaction().build(request).flatMap(builtTransactionDTO -> {
            var finalizedTransaction = builtTransactionDTO.toFinalized(keyPair);
            return account.transaction().finalize(finalizedTransaction, false)
                    .flatMap(finalTxTdo -> account.transaction().submit(finalTxTdo));
        }).onFailure(Utils::toTestFailureException);
    }

}
