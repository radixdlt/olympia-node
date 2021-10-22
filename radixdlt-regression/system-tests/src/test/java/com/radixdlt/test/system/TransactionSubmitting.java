package com.radixdlt.test.system;

import com.radixdlt.application.tokens.Amount;
import com.radixdlt.client.lib.dto.ValidatorDTO;
import com.radixdlt.test.account.Account;
import com.radixdlt.test.system.scaffolding.SystemTest;
import com.radixdlt.test.utils.TestingUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.platform.commons.util.StringUtils;
import org.radix.Radix;

import java.util.Optional;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TransactionSubmitting extends SystemTest {

    protected static final Logger logger = LogManager.getLogger();

    private final static Amount STAKED_AMOUNT = Amount.ofTokens(110);
    private final static int HARDCODED_ACCOUNT_NUMBER = 5;

    private final Account account;
    private final ValidatorDTO firstValidator;

    public TransactionSubmitting() {
        var configuration = getNetwork().getConfiguration();
        var seedPhrase = configuration.getSeedPhrase();
        this.account = StringUtils.isNotBlank(seedPhrase) ?
            initializeAccountFromSeedPhrase(seedPhrase) :
            Account.initialize(configuration.getJsonRpcRootUrl(), configuration.getPrimaryPort(),
                configuration.getSecondaryPort(), TestingUtils.createKeyPairFromNumber(HARDCODED_ACCOUNT_NUMBER));
        logger.info("Using address {} with balances:\n{}", account.getAddressForNetwork(),
            account.getOwnTokenBalances());
        firstValidator = account.validator().list(100, Optional.empty()).getValidators().get(0);
    }

    //@Test
    public void token_transfers() {
        faucet(account);
        var txId = account.transfer(account1, Amount.ofTokens(2), generateTestMessageOptional());
        logger.info("Token transfer txId: {}", txId);
    }

    //@Test
    //@Order(1)
    public void stake() {
        faucet(account, STAKED_AMOUNT);
        faucet(account); // one more faucet call to get tokens to pay for fees
        logger.info("Will stake {} to validator {}", STAKED_AMOUNT, firstValidator);
        var txId = account.stake(firstValidator.getAddress(), STAKED_AMOUNT, generateTestMessageOptional());
        logger.info("Stake tokens txId: {}", txId);
    }

    @Test
    @Order(2)
    public void unstake() {
        logger.info("Unstaking {} from validator {}", STAKED_AMOUNT, firstValidator);
        var txId = account.unstake(firstValidator.getAddress(), STAKED_AMOUNT, Optional.empty());
        logger.info("Unstake tokens txId: {}", txId);
    }

    @Test
    public void mutable_supply_token_creation() {
        var timestamp = System.currentTimeMillis();
        var txId = account.mutableSupplyToken(
            "mtt" + timestamp,
            "mtesttoken" + timestamp,
            "A mutable supply token created for testing. Version " + Radix.systemVersionInfo().get("display"),
            "www.mtesttoken.ico",
            "www.mtesttoken.com");
        logger.info("Mutable supply token txId: {}", txId);
    }

    //@Test
    public void fixed_supply_token_creation() {
        var timestamp = System.currentTimeMillis();
        var supply = Amount.ofTokens(1000000);
        var txId = account.fixedSupplyToken(
            "ftt" + timestamp,
            "ftesttoken_" + timestamp,
            "A fixed supply (" + supply + ") token created for testing. Version " + Radix.systemVersionInfo().get("display"),
            "https://www.ftesttoken.ico",
            "https://www.ftesttoken.com",
            supply);
        logger.info("Fixed supply token txId: {}", txId);
    }

    private Account initializeAccountFromSeedPhrase(String seedPhrase) {
        throw new RuntimeException("Unimplemented");
    }

    private Optional<String> generateTestMessageOptional() {
        return Optional.of("Auto-generated test transaction from version " + Radix.systemVersionInfo().get("display"));
    }

}
