package com.radix.acceptance;

import com.radix.test.Utils;
import com.radix.test.account.Account;
import com.radix.test.network.TestNetwork;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.utils.UInt256;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.util.Lists;

import java.util.List;
import java.util.stream.IntStream;

public abstract class AcceptanceTest {

    private static final Logger logger = LogManager.getLogger();

    /**
     * the faucet always sends the same amount per transaction
     */
    public static final UInt256 FIXED_FAUCET_AMOUNT = Utils.fromMajorToMinor(UInt256.from(10));

    private final TestNetwork testNetwork;
    private final List<Account> accounts;
    protected final Account account1;
    protected final Account account2;

    public AcceptanceTest() {
        testNetwork = TestNetwork.initializeFromEnv();
        accounts = Lists.newArrayList();
        IntStream.range(0, 5).forEach(i -> {
            var account = testNetwork.generateNewAccount().fold(Utils::toTestFailureException, newAccount -> newAccount);
            accounts.add(account);
        });
        account1 = accounts.get(0);
        account2 = accounts.get(1);
    }

    public Account getTestAccount() {
        return accounts.get(0);
    }

    public Account getTestAccount(int number) {
        return accounts.get(number);
    }

    public void faucet(AccountAddress to) {
        testNetwork.faucet(to);
    }

    public TestNetwork getNetwork() {
        return testNetwork;
    }

    /**
     * Calls the faucet for the given account and waits for the faucet tokens to arrive
     */
    public void callFaucetAndWaitForTokens(Account account) {
        faucet(account.getAddress());
        Utils.waitForBalanceToReach(account, FIXED_FAUCET_AMOUNT);
    }

}
