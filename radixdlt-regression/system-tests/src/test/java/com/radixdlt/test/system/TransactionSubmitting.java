package com.radixdlt.test.system;

import com.google.common.collect.Lists;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.identifiers.AID;
import com.radixdlt.test.crypto.BitcoinJBIP32Path;
import com.radixdlt.test.crypto.BitcoinJMnemonicToSeedConverter;
import com.radixdlt.test.crypto.errors.MnemonicException;
import com.radixdlt.test.system.scaffolding.SystemTest;
import com.radixdlt.test.utils.TransactionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

public class TransactionSubmitting extends SystemTest {

    protected static final Logger logger = LogManager.getLogger();

    public TransactionSubmitting() throws MnemonicException {
        byte[] seed = BitcoinJMnemonicToSeedConverter.seedFromMnemonic(List.of(
            "horror", "clap", "section", "trade", "dry", "okay", "olive pledge", "combine", "ball", "exist", "true",
            "seven", "arm", "praise"));
        System.out.println(seed);

        //getNetwork().getConfiguration().getExternalAccountMnemonic();
    }

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

    @BeforeAll
    private static void getA() {

    }

}
