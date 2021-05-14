package com.radix.test.account;

import com.radix.test.Utils;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.NavigationCursor;
import com.radixdlt.client.lib.api.RadixApi;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.client.lib.dto.*;
import com.radixdlt.client.lib.impl.SynchronousRadixApiClient;
import com.radixdlt.client.lib.network.HttpClients;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A wrapper around an api client + a keypair
 */
public final class Account implements RadixApi {

    private static final Logger logger = LogManager.getLogger();

    private final SynchronousRadixApiClient client;
    private final ECKeyPair keyPair;
    private final AccountAddress address;
    private final TokenInfoDTO nativeToken;

    private Account(SynchronousRadixApiClient client, ECKeyPair keyPair, TokenInfoDTO nativeToken) {
        this.client = client;
        this.keyPair = keyPair;
        this.address = AccountAddress.create(keyPair.getPublicKey());
        this.nativeToken = nativeToken;
    }

    public AccountAddress getAddress() {
        return address;
    }

    public ECKeyPair getKeyPair() {
        return keyPair;
    }

    public static Result<Account> initialize(String jsonRpcUrl) {
        return SynchronousRadixApiClient.connect(jsonRpcUrl, HttpClients.getSslAllTrustingClient())
                .flatMap(api -> api.nativeToken().map(nativeToken -> {
                    var newAccount = new Account(api, ECKeyPair.generateNew(), nativeToken);
                    logger.trace("Generated new account with address: {}", newAccount.getAddress());
                    logger.trace("New account connected to {}", jsonRpcUrl);
                    logger.trace("Network's native token is {}({})", nativeToken.getName(), nativeToken.getSymbol());
                    return newAccount;
                }));
    }

    /**
     * returns the (already queried) native token
     */
    public TokenInfoDTO getNativeToken() {
        return nativeToken;
    }


    public Result<BalanceDTO> ownNativeTokenBalance() {
        BalanceDTO zeroNativeTokenBalance = BalanceDTO.create(nativeToken.getRri(), UInt256.ZERO);
        return ownTokenBalances().map(tokenBalancesDTO -> {
            if (tokenBalancesDTO.getTokenBalances().size() == 0) {
                return zeroNativeTokenBalance;
            }
            var balances = tokenBalancesDTO.getTokenBalances().stream().filter(balanceDTO ->
                    balanceDTO.getRri().equals(nativeToken.getRri())).collect(Collectors.toList());
            return balances.isEmpty() ? zeroNativeTokenBalance : balances.get(0);
        });
    }

    public BalanceDTO getOwnNativeTokenBalance() {
        return ownNativeTokenBalance().fold(Utils::toRuntimeException, balanceDTO -> balanceDTO);
    }

    public Result<TokenBalancesDTO> ownTokenBalances() {
        return client.tokenBalances(address);
    }

    @Override
    public Result<NetworkIdDTO> networkId() {
        return client.networkId();
    }

    @Override
    public Result<TokenInfoDTO> nativeToken() {
        return client.nativeToken();
    }

    @Override
    public Result<TokenInfoDTO> tokenInfo(String rri) {
        return client.tokenInfo(rri);
    }

    @Override
    public Result<TokenBalancesDTO> tokenBalances(AccountAddress address) {
        return client.tokenBalances(address);
    }

    @Override
    public Result<TransactionHistoryDTO> transactionHistory(AccountAddress address, int size, Optional<NavigationCursor> cursor) {
        return client.transactionHistory(address, size, cursor);
    }

    @Override
    public Result<TransactionDTO> lookupTransaction(AID txId) {
        return client.lookupTransaction(txId);
    }

    @Override
    public Result<List<StakePositionsDTO>> stakePositions(AccountAddress address) {
        return client.stakePositions(address);
    }

    @Override
    public Result<List<UnstakePositionsDTO>> unstakePositions(AccountAddress address) {
        return client.unstakePositions(address);
    }

    @Override
    public Result<TransactionStatusDTO> statusOfTransaction(AID txId) {
        return client.statusOfTransaction(txId);
    }

    @Override
    public Result<NetworkStatsDTO> networkTransactionThroughput() {
        return client.networkTransactionThroughput();
    }

    @Override
    public Result<NetworkStatsDTO> networkTransactionDemand() {
        return client.networkTransactionDemand();
    }

    @Override
    public Result<ValidatorsResponseDTO> validators(int size, Optional<NavigationCursor> cursor) {
        return client.validators(size, cursor);
    }

    @Override
    public Result<ValidatorDTO> lookupValidator(String validatorAddress) {
        return client.lookupValidator(validatorAddress);
    }

    @Override
    public Result<BuiltTransactionDTO> buildTransaction(TransactionRequest request) {
        return client.buildTransaction(request);
    }

    @Override
    public Result<TxDTO> finalizeTransaction(FinalizedTransaction request) {
        return client.finalizeTransaction(request);
    }

    @Override
    public Result<TxDTO> submitTransaction(FinalizedTransaction request) {
        return client.submitTransaction(request);
    }

}
