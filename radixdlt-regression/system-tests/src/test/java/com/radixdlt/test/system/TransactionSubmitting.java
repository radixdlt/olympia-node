package com.radixdlt.test.system;

import com.radixdlt.application.tokens.Amount;
import com.radixdlt.identifiers.AID;
import com.radixdlt.test.system.scaffolding.SystemTest;
import com.radixdlt.test.utils.TransactionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.util.Optional;

public class TransactionSubmitting extends SystemTest {

    protected static final Logger logger = LogManager.getLogger();

    @Test
    public void token_transfers() {
        AID txId = TransactionUtils.nativeTokenTransfer(account1, account2, Amount.ofTokens(10), Optional.of("a"));
        logger.info(txId);
    }

    //@Test
    public void stake() {

    }

    //@Test
    public void unstake() {

    }

    //@Test
    public void mutable_supply_token_creation() {

    }

    //@Test
    public void fixed_supply_token_creation() {

    }

}
