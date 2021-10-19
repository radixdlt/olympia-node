package com.radixdlt.test.account;

import com.radixdlt.test.utils.TransactionUtils;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.ValidatorAddress;
import com.radixdlt.client.lib.api.sync.ImperativeRadixApi;
import com.radixdlt.client.lib.dto.Balance;
import com.radixdlt.client.lib.dto.TokenBalances;
import com.radixdlt.client.lib.dto.TokenInfo;
import com.radixdlt.client.lib.dto.TransactionDTO;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.UInt256;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A wrapper around an imperative api client + a keypair
 */
public final class Account implements ImperativeRadixApi, RadixAccount {
    private static final Logger logger = LogManager.getLogger();

    private final ImperativeRadixApi client;
    private final ECKeyPair keyPair;
    private final AccountAddress address;
    private final TokenInfo nativeToken;

    private Account(ImperativeRadixApi client, ECKeyPair keyPair, TokenInfo nativeToken) {
        this.client = client;
        this.keyPair = keyPair;
        this.address = AccountAddress.create(keyPair.getPublicKey());
        this.nativeToken = nativeToken;
    }

    @Override
    public Account withTrace() {
        client.withTrace();
        return this;
    }

    @Override
    public ImperativeRadixApi withTimeout(Duration timeout) {
        client.withTimeout(timeout);
        return this;
    }

    @Override
    public Network network() {
        return client.network();
    }

    @Override
    public Transaction transaction() {
        return client.transaction();
    }

    @Override
    public Token token() {
        return client.token();
    }

    @Override
    public Local local() {
        return client.local();
    }

    @Override
    public SingleAccount account() {
        return client.account();
    }

    @Override
    public Validator validator() {
        return client.validator();
    }

    @Override
    public Api api() {
        return client.api();
    }

    @Override
    public Consensus consensus() {
        return client.consensus();
    }

    @Override
    public Mempool mempool() {
        return client.mempool();
    }

    @Override
    public RadixEngine radixEngine() {
        return client.radixEngine();
    }

    @Override
    public Sync sync() {
        return client.sync();
    }

    @Override
    public Ledger ledger() {
        return client.ledger();
    }

    public AccountAddress getAddress() {
        return address;
    }

    public ECKeyPair getKeyPair() {
        return keyPair;
    }

    public static Account initialize(String jsonRpcUrl) {
        var api = ImperativeRadixApi.connect(jsonRpcUrl);
        var nativeToken = api.token().describeNative();
        var newAccount = new Account(api, ECKeyPair.generateNew(), nativeToken);
        logger.trace("Generated new account with address: {}", newAccount.getAddress());
        logger.trace("New account connected to {}", jsonRpcUrl);
        logger.trace("Network's native token is {}({})", nativeToken.getName(), nativeToken.getSymbol());
        return newAccount;
    }

    public TokenInfo getNativeToken() {
        return nativeToken;
    }

    public Balance getOwnNativeTokenBalance() {
        Balance zeroNativeTokenBalance = Balance.create(nativeToken.getRri(), UInt256.ZERO);
        var balances = getOwnTokenBalances().getTokenBalances().stream().filter(balance ->
            balance.getRri().equals(nativeToken.getRri())).collect(Collectors.toList());
        return balances.isEmpty() ? zeroNativeTokenBalance : balances.get(0);
    }

    public TokenBalances getOwnTokenBalances() {
        return client.account().balances(address);
    }

    @Override
    public TransactionDTO lookup(AID txID) {
        return TransactionUtils.lookupTransaction(this, txID);
    }

    @Override
    public AID transfer(Account destination, Amount amount, Optional<String> message) {
        return TransactionUtils.nativeTokenTransfer(this, destination, amount, message);
    }

    @Override
    public AID stake(ValidatorAddress validatorAddress, Amount amount) {
        return TransactionUtils.stake(this, validatorAddress, amount);
    }

    @Override
    public AID fixedSupplyToken(String symbol, String name, String description, String iconUrl,
                                String tokenUrl, Amount supply) {
        return TransactionUtils.createFixedSupplyToken(this, symbol, name, description, iconUrl, tokenUrl, supply);
    }
}
