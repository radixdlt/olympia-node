package com.radixdlt.test.system;

import com.radixdlt.application.tokens.Amount;
import com.radixdlt.client.lib.dto.ValidatorDTO;
import com.radixdlt.test.account.Account;
import com.radixdlt.test.crypto.BitcoinJBIP32Path;
import com.radixdlt.test.crypto.DefaultHDKeyPairDerivation;
import com.radixdlt.test.crypto.errors.HDPathException;
import com.radixdlt.test.crypto.errors.MnemonicException;
import com.radixdlt.test.network.RadixNetworkConfiguration;
import com.radixdlt.test.system.scaffolding.SystemTest;
import com.radixdlt.test.utils.TestingUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.StringUtils;
import org.radix.Radix;

import java.util.Optional;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Transactions extends SystemTest {

    protected static final Logger logger = LogManager.getLogger();

    private static final String BIP32_PATH = "m/44'/0'/0'";
    private static final Amount AMOUNT_TO_STAKE = Amount.ofTokens(110);
    private static final Amount TOKENS_TO_MINT = Amount.ofTokens(105020);
    private static final String VERSION_STRING = Radix.systemVersionInfo().get("system_version").get("version_string").toString();

    // in case there is no seed phrase, we use a known address for this test
    private static final int HARDCODED_ADDRESS_PRIVATE_KEY = 11;

    private final Account account;
    private final ValidatorDTO firstValidator;

    public Transactions() {
        // env properties specific to this test:
        var seedPhrase = TestingUtils.getEnvWithDefault("RADIXDLT_TESTING_ACCOUNT_SEED_PHRASE", "");

        var configuration = getNetwork().getConfiguration();
        this.account = StringUtils.isNotBlank(seedPhrase)
            ? initializeAccountFromSeedPhrase(configuration, seedPhrase)
            : Account.initialize(configuration.getJsonRpcRootUrl(), configuration.getPrimaryPort(),
            configuration.getSecondaryPort(), TestingUtils.createKeyPairFromNumber(HARDCODED_ADDRESS_PRIVATE_KEY));
        logger.info("Using address {} with balances:\n{}", account.getAddressForNetwork(),
            account.getOwnTokenBalances());
        firstValidator = account.validator().list(100, Optional.empty()).getValidators().get(0);
    }

    @BeforeEach
    public void callFaucet() {
        faucet(account);
    }

    @Test
    public void token_transfer() {
        var txId = account.transfer(account1, Amount.ofMicroTokens(2500), createTestMessageOptional());
        logger.info("Token transfer txId: {}", txId);
    }

    @Test
    @Order(1)
    public void stake() {
        faucet(account, AMOUNT_TO_STAKE);
        logger.info("Staking {} to validator {}...", AMOUNT_TO_STAKE, firstValidator.getAddress().toString());
        var txId = account.stake(firstValidator.getAddress(), AMOUNT_TO_STAKE, createTestMessageOptional());
        logger.info("Stake tokens txId: {}", txId);
    }

    @Test
    @Order(2)
    public void unstake() {
        var nid = account.network().id().getNetworkId();
        logger.info("Unstaking {} from validator {}...", AMOUNT_TO_STAKE, firstValidator.getAddress().toString(nid));
        var txId = account.unstake(firstValidator.getAddress(), AMOUNT_TO_STAKE, Optional.empty());
        logger.info("Unstake tokens txId: {}", txId);
    }

    @Test
    @Order(3)
    public void mutable_supply_token_creation() {
        faucet(account, Amount.ofTokens(100));
        var timestamp = System.currentTimeMillis();
        var txId = account.mutableSupplyToken(
            "mtt" + timestamp,
            "mtesttoken" + timestamp,
            "A mutable supply token created for testing. Version " + VERSION_STRING,
            "https://www.mtesttoken.ico",
            "https://www.mtesttoken.com");
        logger.info("Mutable supply token txId: {}", txId);
        var lookup = account.transaction().lookup(txId);
    }

    @Test
    @Order(4)
    public void mint_mutable_supply_token() {
        var txId = account.mint(TOKENS_TO_MINT, "mtt", createTestMessageOptional());
        logger.info("Token {} mint txId: {}", "mtt", txId);
    }

    @Test
    @Order(5)
    public void burn_mutable_supply_token() {
        var txId = account.burn(TOKENS_TO_MINT, "mtt", createTestMessageOptional());
        logger.info("Token {} burn txId: {}", "mtt", txId);
    }

    @Test
    public void fixed_supply_token_creation() {
        faucet(account, Amount.ofTokens(100));
        var timestamp = System.currentTimeMillis();
        var supply = Amount.ofTokens(1000000);
        var txId = account.fixedSupplyToken(
            "ftt" + timestamp,
            "ftesttoken_" + timestamp,
            "A fixed supply (" + supply + ") token created for testing. Version " + VERSION_STRING,
            "https://www.ftesttoken.ico",
            "https://www.ftesttoken.com",
            supply);
        logger.info("Fixed supply token txId: {}", txId);
    }

    private Account initializeAccountFromSeedPhrase(RadixNetworkConfiguration configuration, String seedPhrase) {
        logger.info("Will generate keypair from seed phrase...");
        try {
            var keyPairDerivation = DefaultHDKeyPairDerivation.fromMnemonicString(seedPhrase);
            var bip32path = BitcoinJBIP32Path.fromString(BIP32_PATH);
            var keyPair = keyPairDerivation.deriveKeyAtPath(bip32path).keyPair();
            return Account.initialize(configuration.getJsonRpcRootUrl(), configuration.getPrimaryPort(),
                configuration.getSecondaryPort(), keyPair);
        } catch (MnemonicException | HDPathException e) {
            throw new RuntimeException("Could not generate keypair from seed phrase", e);
        }
    }

    private Optional<String> createTestMessageOptional() {
        return Optional.of("Auto-generated test transaction from version '" + VERSION_STRING + "'");
    }

}
