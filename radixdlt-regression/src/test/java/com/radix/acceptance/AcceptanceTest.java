package com.radix.acceptance;

import com.radix.test.Utils;
import com.radix.test.account.Account;
import com.radix.test.network.TestNetwork;
import com.radixdlt.client.lib.api.AccountAddress;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.util.Lists;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.Assert.fail;

public abstract class AcceptanceTest {

    private static final Logger logger = LogManager.getLogger();

    private final TestNetwork testNetwork;
    private final List<Account> accounts;

    public AcceptanceTest() {
        testNetwork = TestNetwork.initializeFromEnv();
        accounts = Lists.newArrayList();
        IntStream.range(0,5).forEach(i -> {
            Account account = testNetwork.generateNewAccount().fold(Utils::toRuntimeException, newAccount -> newAccount);
            accounts.add(account);
        });
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

}
