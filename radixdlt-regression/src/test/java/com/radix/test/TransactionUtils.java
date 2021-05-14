package com.radix.test;

import com.radix.test.account.Account;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.client.lib.dto.FinalizedTransaction;
import com.radixdlt.client.lib.dto.TxDTO;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import static org.awaitility.Awaitility.await;

public final class TransactionUtils {

    public static TransactionRequest createTransferRequest(AccountAddress from, AccountAddress to, String tokenRri, UInt256 amount,
                                                           String message) {
        return TransactionRequest.createBuilder()
                .transfer(from, to, amount, tokenRri)
                .message(message)
                .build();
    }

    public static Result<TxDTO> performNativeTokenTransfer(Account sender, Account receiver, int amount, String message) {
        var request = TransactionUtils.createTransferRequest(sender.getAddress(), receiver.getAddress(),
                sender.getNativeToken().getRri(), Utils.toMajor(amount), message);
        return performTransaction(sender, request);
    }

    public static Result<TxDTO> performNativeTokenTransfer(Account sender, Account receiver, UInt256 amount, String message) {
        var request = TransactionUtils.createTransferRequest(sender.getAddress(), receiver.getAddress(),
                sender.getNativeToken().getRri(), amount, message);
        return performTransaction(sender, request);
    }

    public static Result<TxDTO> performTransaction(Account account, TransactionRequest request) {
        ECKeyPair keyPair = account.getKeyPair();
        return account.buildTransaction(request).flatMap(builtTransactionDTO -> {
            FinalizedTransaction finalizedTransaction = builtTransactionDTO.toFinalized(keyPair);
            return account.finalizeTransaction(finalizedTransaction)
                    .flatMap(finalTxTdo -> account.submitTransaction(finalizedTransaction.withTxId(finalTxTdo.getTxId())));
        }).onFailure(Utils::toRuntimeException);
    }

}
