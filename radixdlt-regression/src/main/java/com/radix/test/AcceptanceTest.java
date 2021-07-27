package com.radix.test;

import com.google.common.collect.Lists;
import com.radix.test.account.Account;
import com.radix.test.network.RadixNetwork;
import com.radix.test.utils.TransactionUtils;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.dto.Balance;
import com.radixdlt.identifiers.AID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.stream.IntStream;

import static org.awaitility.Awaitility.await;

public abstract class AcceptanceTest {

    private static final Logger logger = LogManager.getLogger();

    private final RadixNetwork radixNetwork;
    private final List<Account> accounts;
    protected final Account account1;
    protected final Account account2;

    // since test steps happen synchronously, this variable is used to store some state between steps
    protected AID txBuffer;

    public AcceptanceTest() {
        radixNetwork = RadixNetwork.initializeFromEnv();
        accounts = Lists.newArrayList();
        IntStream.range(0, 6).forEach(i -> {
            var account = radixNetwork.generateNewAccount();
            accounts.add(account);
        });
        account1 = accounts.get(0);
        account2 = accounts.get(1);
    }

    public Account getTestAccount(int number) {
        return accounts.get(number);
    }

    public String faucet(AccountAddress to) {
        return radixNetwork.faucet(to);
    }

    public RadixNetwork getNetwork() {
        return radixNetwork;
    }

    /**
     * Calls the faucet for the given account and waits for transaction confirmation
     *
     * @return the txID of the faucet's transaction
     */
    public String faucet(Account account) {
        Balance balanceBeforeFaucet = account.getOwnNativeTokenBalance();
        String txID = faucet(account.getAddress());
        TransactionUtils.waitForConfirmation(account, AID.from(txID));
        await().until(() ->
            // wait until the account's balance increases, just to be sure that the faucet delivered something
            balanceBeforeFaucet.getAmount().compareTo(account.getOwnNativeTokenBalance().getAmount()) == -1
        );
        return txID;
    }

    /**
     * Repeatedly calls the faucet until the given amount is credited
     */
    public void faucet(Account account, Amount amount) {
        Balance originalBalance = account.getOwnNativeTokenBalance();
        while (account.getOwnNativeTokenBalance().getAmount().subtract(originalBalance.getAmount())
            .compareTo(amount.toSubunits()) == -1) {
            faucet(account);
        }
    }

}
