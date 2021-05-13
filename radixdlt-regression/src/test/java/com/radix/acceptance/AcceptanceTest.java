package com.radix.acceptance;

import com.radix.test.account.Account;
import com.radix.test.network.TestNetwork;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.utils.functional.Result;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.util.List;

public abstract class AcceptanceTest {

    private static final Logger logger = LogManager.getLogger();

    private final TestNetwork testNetwork;
    private Result<Account> account;

    public AcceptanceTest() {
        testNetwork = TestNetwork.initializeFromEnv();
        account = testNetwork.generateNewAccount();
    }

    public Result<Account> getTestAccount() {
        return account;
    }

    public void faucet(AccountAddress to) {
        testNetwork.faucet(to);
    }

    public TestNetwork getNetwork() {
        return testNetwork;
    }

}
