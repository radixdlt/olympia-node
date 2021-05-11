package com.radix.acceptance;

import com.radix.test.account.Account;
import com.radix.test.network.TestNetwork;
import com.radixdlt.utils.functional.Result;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AcceptanceTest {

    private static final Logger logger = LogManager.getLogger();

    private final TestNetwork testNetwork;
    private Result<Account> account;

    public AcceptanceTest() {
        testNetwork = TestNetwork.initializeFromSystemProperties();
        account = testNetwork.generateNewAccount();
    }

    public Result<Account> getTestAccount() {
        return account;
    }

    public TestNetwork getNetwork() {
        return testNetwork;
    }

}
