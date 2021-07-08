package com.radix.acceptance;

import com.radix.test.TransactionUtils;
import com.radix.test.Utils;
import com.radix.test.account.Account;
import com.radix.test.network.RadixNetwork;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.identifiers.AID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.util.Lists;

import java.util.List;
import java.util.stream.IntStream;

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
        String txID = faucet(account.getAddress());
        TransactionUtils.waitForConfirmation(account, AID.from(txID));
        return txID;
    }

}
