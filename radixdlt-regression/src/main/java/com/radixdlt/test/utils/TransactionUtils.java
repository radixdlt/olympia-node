package com.radixdlt.test.utils;

import com.radixdlt.application.tokens.Amount;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.client.lib.api.ValidatorAddress;
import com.radixdlt.client.lib.api.sync.RadixApiException;
import com.radixdlt.client.lib.dto.TransactionDTO;
import com.radixdlt.client.lib.dto.TransactionStatus;
import com.radixdlt.identifiers.AID;
import com.radixdlt.test.account.Account;
import com.radixdlt.utils.UInt256;
import org.awaitility.core.ConditionTimeoutException;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;

/**
 * Various helper utils that make working with our api easier. Most of the methods here use the imperative API and
 * are probably suited for simple use cases or testing.
 */
public final class TransactionUtils {

    public static final Duration DEFAULT_TX_CONFIRMATION_PATIENCE = Duration.ofMinutes(1);

    private TransactionUtils() {

    }

    public static TransactionRequest createTokenTransferRequest(AccountAddress from, AccountAddress to, String tokenRri,
                                                                UInt256 amount, Optional<String> message) {
        return message.map(s -> TransactionRequest.createBuilder(from)
            .transfer(from, to, amount, tokenRri)
            .message(s)
            .build()).orElseGet(() -> TransactionRequest.createBuilder(from)
            .transfer(from, to, amount, tokenRri)
            .build());
    }

    public static TransactionRequest createStakingRequest(AccountAddress from, ValidatorAddress unstakeFrom,
                                                            Amount stake, Optional<String> message) {
        return message.map(s -> TransactionRequest.createBuilder(from)
            .stake(from, unstakeFrom, stake.toSubunits())
            .message(s)
            .build()).orElseGet(() -> TransactionRequest.createBuilder(from)
            .stake(from, unstakeFrom, stake.toSubunits())
            .build());
    }

    public static TransactionRequest createUnstakingRequest(AccountAddress from, ValidatorAddress unstakeFrom,
                                                            Amount stake, Optional<String> message) {
        return message.map(s -> TransactionRequest.createBuilder(from)
            .unstake(from, unstakeFrom, stake.toSubunits())
            .message(s)
            .build()).orElseGet(() -> TransactionRequest.createBuilder(from)
            .unstake(from, unstakeFrom, stake.toSubunits())
            .build());
    }

    public static TransactionRequest createMintRequest(AccountAddress from, Amount amount, String rri, Optional<String> message) {
        return message.map(s -> TransactionRequest.createBuilder(from)
            .mint(from, amount.toSubunits(), rri)
            .message(s)
            .build()).orElseGet(() -> TransactionRequest.createBuilder(from)
            .mint(from, amount.toSubunits(), rri)
            .build());
    }

    public static TransactionRequest createBurnRequest(AccountAddress from, Amount amount, String rri, Optional<String> message) {
        return message.map(s -> TransactionRequest.createBuilder(from)
            .burn(from, amount.toSubunits(), rri)
            .message(s)
            .build()).orElseGet(() -> TransactionRequest.createBuilder(from)
            .burn(from, amount.toSubunits(), rri)
            .build());
    }

    /**
     * Stakes tokens and waits for transaction confirmation
     */
    public static AID stake(Account account, ValidatorAddress to, Amount amount, Optional<String> message) {
        var request = createStakingRequest(account.getAddress(), to, amount, message);
        return buildFinalizeAndSubmitTransaction(account, request, true);
    }

    /**
     * Unstakes tokens and waits for transaction confirmation
     */
    public static AID unstake(Account account, ValidatorAddress validatorAddress, Amount amount, Optional<String> message) {
        var request = createUnstakingRequest(account.getAddress(), validatorAddress, amount, message);
        return buildFinalizeAndSubmitTransaction(account, request, true);
    }

    /**
     * Mints tokens and waits for transaction confirmation
     */
    public static AID mint(Account account, Amount amount, String rri, Optional<String> message) {
        var request = createMintRequest(account.getAddress(), amount, rri, message);
        return buildFinalizeAndSubmitTransaction(account, request, true);
    }

    /**
     * Burns tokens and waits for transaction confirmation
     */
    public static AID burn(Account account, Amount amount, String rri, Optional<String> message) {
        var request = createBurnRequest(account.getAddress(), amount, rri, message);
        return buildFinalizeAndSubmitTransaction(account, request, true);
    }

    /**
     * Executes an XRD transfer and waits for transaction confirmation
     */
    public static AID nativeTokenTransfer(Account sender, Account receiver, Amount amount, Optional<String> message) {
        var request = TransactionUtils.createTokenTransferRequest(sender.getAddress(), receiver.getAddress(),
            sender.getNativeToken().getRri(), amount.toSubunits(), message);
        return buildFinalizeAndSubmitTransaction(sender, request, true);
    }

    /**
     * Submits a fixed supply token creation transaction and waits for its confirmation
     */
    public static AID createFixedSupplyToken(Account creator, String symbol, String name, String description,
                                             String iconUrl, String tokenUrl, Amount supply) {
        var key = creator.getKeyPair().getPublicKey();
        var request = TransactionRequest.createBuilder(creator.getAddress())
            .createFixed(creator.getAddress(), key, symbol, name, description, iconUrl, tokenUrl, supply.toSubunits())
            .build();
        return buildFinalizeAndSubmitTransaction(creator, request, true);
    }

    public static AID createMutableSupplyToken(Account creator, String symbol, String name, String description,
                                               String iconUrl, String tokenUrl) {
        var key = creator.getKeyPair().getPublicKey();
        var request = TransactionRequest.createBuilder(creator.getAddress())
            .createMutable(key, symbol, name, Optional.of(description), Optional.of(iconUrl), Optional.of(tokenUrl)).build();
        return buildFinalizeAndSubmitTransaction(creator, request, true);
    }


    /**
     * Builds, finalizes and submits a transaction. Can optionally wait for it to become CONFIRMED
     *
     * @return the {@link AID} of the submitted transaction
     */
    public static AID buildFinalizeAndSubmitTransaction(Account account, TransactionRequest request, boolean waitForConfirmation) {
        var keyPair = account.getKeyPair();
        var builtTransaction = account.transaction().build(request);
        var finalizedTransaction = account.transaction().finalize(builtTransaction.toFinalized(keyPair), false);
        var submittedTransaction = account.transaction().submit(finalizedTransaction);

        if (waitForConfirmation) {
            waitForConfirmation(account, submittedTransaction.getTxId());
        }

        return submittedTransaction.getTxId();
    }

    /**
     * Will block until the a transaction for the given txID is found
     */
    public static TransactionDTO lookupTransaction(Account account, AID txId) {
        AtomicReference<TransactionDTO> transaction = new AtomicReference<>();
        try {
            await().atMost(DEFAULT_TX_CONFIRMATION_PATIENCE).ignoreException(RadixApiException.class).until(() -> {
                transaction.set(account.transaction().lookup(txId));
                return true;
            });
        } catch (ConditionTimeoutException e) {
            throw new TestFailureException("Transaction " + txId + " was not found within " + DEFAULT_TX_CONFIRMATION_PATIENCE);
        }
        return transaction.get();
    }

    /**
     * Will block until the given transaction is CONFIRMED
     */
    public static void waitForConfirmation(Account account, AID txId, Duration patience) {
        try {
            await().atMost(patience).until(() -> {
                var status = account.transaction().status(txId);
                return status.getStatus().equals(TransactionStatus.CONFIRMED);
            });
        } catch (ConditionTimeoutException e) {
            throw new TestFailureException("Transaction (" + txId + ") was not CONFIRMED within " + patience);
        }
    }

    /**
     * Will block until the given transaction is CONFIRMED. Will have the default patience
     */
    public static void waitForConfirmation(Account account, AID txId) {
        waitForConfirmation(account, txId, DEFAULT_TX_CONFIRMATION_PATIENCE);
    }

}
