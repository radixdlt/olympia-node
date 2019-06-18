package com.radix.acceptance.mint_multi_issuance_tokens;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.application.translate.tokens.TokenOverMintException;
import com.radixdlt.client.application.translate.tokens.TokenUnitConversions;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.particles.RRI;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomReceivedAction;
import com.radixdlt.client.core.network.actions.SubmitAtomRequestAction;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.radix.utils.UInt256;

import com.google.common.collect.Lists;
import com.radix.acceptance.SpecificProperties;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.application.translate.tokens.MintTokensAction;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionsState;
import com.radixdlt.client.application.translate.tokens.UnknownTokenException;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.Bootstrap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import io.reactivex.observers.BaseTestConsumer.TestWaitStrategy;
import io.reactivex.observers.TestObserver;

/**
 * See <a href="https://radixdlt.atlassian.net/browse/RLAU-94">RLAU-94</a>.
 */
public class MintMultiIssuanceTokens {
	private static final String ADDRESS = "address";
	private static final String NAME = "name";
	private static final String SYMBOL = "symbol";
	private static final String DESCRIPTION = "description";
	private static final String INITIAL_SUPPLY = "initialSupply";
	private static final String NEW_SUPPLY = "newSupply";
	private static final String GRANULARITY = "granularity";

	private static final long TIMEOUT_MS = 10_000L; // Timeout in milliseconds

	private RadixApplicationAPI api;
	private RadixIdentity identity;
	private RadixApplicationAPI otherApi;
	private RadixIdentity otherIdentity;
	private final SpecificProperties properties = SpecificProperties.of(
		ADDRESS,        "unknown",
		NAME,           "RLAU-40 Test token",
		SYMBOL,			"RLAU",
		DESCRIPTION,	"RLAU-40 Test token",
		INITIAL_SUPPLY,	"1000000000",
		NEW_SUPPLY,		"1000000000",
		GRANULARITY,	BigDecimal.ONE.scaleByPowerOfTen(-18).toString()
	);
	private final List<TestObserver<SubmitAtomAction>> observers = Lists.newArrayList();

	@Given("^a library client who owns an account and created token \"([^\"]*)\" with (\\d+) initial supply and is listening to the state of \"([^\"]*)\"$")
	public void a_library_client_who_owns_an_account_and_created_token_with_initial_supply_and_is_listening_to_the_state_of(
			String symbol1, int initialSupply, String symbol2) throws Throwable {
		assertEquals(symbol1, symbol2); // Only case we handle at the moment

		setupApi();
		this.properties.put(SYMBOL, symbol1);
		this.properties.put(INITIAL_SUPPLY, Integer.toString(initialSupply));
		createToken(TokenSupplyType.MUTABLE);
		awaitAtomStatus(AtomStatus.STORED);
		// Listening on state automatic for library
	}

	@Given("^a library client who owns an account and created a token with (\\d+) initial subunit supply$")
	public void a_library_client_who_owns_an_account_and_created_a_token_with_initial_subunit_supply(int initialUnscaledSupply) throws Throwable {
		setupApi();
		this.properties.put(INITIAL_SUPPLY, BigDecimal.valueOf(initialUnscaledSupply).scaleByPowerOfTen(-18).toString());
		createToken(CreateTokenAction.TokenSupplyType.MUTABLE);
		awaitAtomStatus(AtomStatus.STORED);
	}

	@Given("^a library client who owns an account and created a token with 2\\^(\\d+) initial subunit supply and is listening to the state of the token$")
	public void a_library_client_who_owns_an_account_and_created_a_token_with_initial_subunit_supply_and_is_listening_to_the_state_of_the_token(int pow2)
			throws Throwable {
		setupApi();
		this.properties.put(INITIAL_SUPPLY, BigDecimal.valueOf(2).pow(pow2).scaleByPowerOfTen(-18).toString());
		createToken(CreateTokenAction.TokenSupplyType.MUTABLE);
		awaitAtomStatus(AtomStatus.STORED);
	}

	@Given("^a library client who owns an account where token \"([^\"]*)\" does not exist$")
	public void a_library_client_who_owns_an_account_where_token_does_not_exist(String symbol) throws Throwable {
		setupApi();
		// No tokens exist for this account, because it is a freshly created account
		RRI tokenClass = RRI.of(api.getMyAddress(), symbol);
		TokenDefinitionsState tokenClassesState = api.getMyTokenClasses()
			.firstOrError()
			.blockingGet();
		assertFalse(tokenClassesState.getState().containsKey(tokenClass));
	}

	@Given("^a library client who does not own a token class \"([^\"]*)\" on another account$")
	public void a_library_client_who_does_not_own_a_token_class_on_another_account(String symbol) throws Throwable {
		setupApi();

		this.properties.put(SYMBOL, symbol);
		createToken(this.otherApi, TokenSupplyType.MUTABLE);
		awaitAtomStatus(AtomStatus.STORED);

		this.properties.put(ADDRESS, this.otherApi.getMyAddress().toString());
	}

	@When("^the client executes mint (\\d+) \"([^\"]*)\" tokens$")
	public void the_client_executes_mint_tokens(int newSupply, String symbol) throws Throwable {
		mintTokens(BigDecimal.valueOf(newSupply), symbol, RadixAddress.from(this.properties.get(ADDRESS)));
	}

	@When("^the client executes mint (\\d+) tokens$")
	public void the_client_executes_mint_tokens(int newSupply) throws Throwable {
		mintTokens(BigDecimal.valueOf(newSupply), this.properties.get(SYMBOL), RadixAddress.from(this.properties.get(ADDRESS)));
	}

	@When("^the client executes mint 2\\^(\\d+) subunit tokens$")
	public void the_client_executes_mint_subunit_tokens(int pow2) throws Throwable {
		mintTokens(BigDecimal.valueOf(2).pow(pow2).scaleByPowerOfTen(-18), this.properties.get(SYMBOL), RadixAddress.from(this.properties.get(ADDRESS)));
	}

	@Then("^the client should be notified that \"([^\"]*)\" token has a total supply of (\\d+)$")
	public void theClientShouldBeNotifiedThatTokenHasATotalSupplyOf(String symbol, int supply) throws Throwable {
		awaitAtomStatus(AtomStatus.STORED);
		RRI tokenClass = RRI.of(api.getMyAddress(), symbol);
		// Ensure balance is up-to-date.
		BigDecimal tokenBalanceDecimal = api.getBalance(api.getMyAddress(), tokenClass)
			.firstOrError()
			.blockingGet();
		UInt256 tokenBalance = TokenUnitConversions.unitsToSubunits(tokenBalanceDecimal);
		UInt256 requiredBalance = TokenUnitConversions.unitsToSubunits(supply);
		assertEquals(requiredBalance, tokenBalance);
	}

	@Then("^the client should be notified that the action failed because cannot mint with (\\d+) tokens$")
	public void the_client_should_be_notified_that_the_action_failed_because_cannot_mint_with_tokens(int count) throws Throwable {
		assertEquals(0, count); // Only thing we check for here
		awaitAtomException(IllegalArgumentException.class, "Mint amount must be greater than 0.");
	}

	@Then("^the client should be notified that the action failed because it reached the max allowed number of tokens of 2\\^256 - 1$")
	public void the_client_should_be_notified_that_the_action_failed_because_it_reached_the_max_allowed_number_of_tokens_of() throws Throwable {
		awaitAtomException(TokenOverMintException.class, "would overflow maximum");
	}

	@Then("^the client should be notified that the action failed because \"([^\"]*)\" does not exist$")
	public void the_client_should_be_notified_that_the_action_failed_because_does_not_exist(String arg1) throws Throwable {
		awaitAtomException(UnknownTokenException.class, "Unknown token");
	}

	@Then("^the client should be notified that the action failed because the client does not have permission to mint those tokens$")
	public void the_client_should_be_notified_that_the_action_failed_because_the_client_does_not_have_permission_to_mint_those_tokens() throws Throwable {
		awaitAtomValidationError("must be signed by token owner");
	}

	private void setupApi() {
		this.identity = RadixIdentities.createNew();
		this.api = RadixApplicationAPI.create(Bootstrap.LOCALHOST_SINGLENODE, this.identity);

		this.otherIdentity = RadixIdentities.createNew();
		this.otherApi = RadixApplicationAPI.create(Bootstrap.LOCALHOST_SINGLENODE, this.otherIdentity);

		// Reset data
		this.properties.clear();
		this.observers.clear();

		this.properties.put(ADDRESS, api.getMyAddress().toString());
	}

	private void createToken(CreateTokenAction.TokenSupplyType tokenCreateSupplyType) {
		createToken(this.api, tokenCreateSupplyType);
	}

	private void createToken(RadixApplicationAPI api, CreateTokenAction.TokenSupplyType tokenCreateSupplyType) {
		TestObserver<SubmitAtomAction> observer = new TestObserver<>();
		api.createToken(
				this.properties.get(NAME),
				this.properties.get(SYMBOL),
				this.properties.get(DESCRIPTION),
				new BigDecimal(this.properties.get(INITIAL_SUPPLY)),
				new BigDecimal(this.properties.get(GRANULARITY)),
				tokenCreateSupplyType)
			.toObservable()
			.doOnNext(System.out::println)
			.subscribe(observer);
		this.observers.add(observer);
	}

	private void mintTokens(BigDecimal amount, String symbol, RadixAddress address) {
		RRI tokenClass = RRI.of(address, symbol);
		MintTokensAction mta = MintTokensAction.create(tokenClass, amount);
		TestObserver<SubmitAtomAction> observer = new TestObserver<>();
		api.execute(mta)
			.toObservable()
			.doOnNext(System.out::println)
			.subscribe(observer);
		this.observers.add(observer);
	}

	private void awaitAtomStatus(AtomStatus... finalStates) {
		awaitAtomStatus(this.observers.size(), finalStates);
	}

	private void awaitAtomStatus(int atomNumber, AtomStatus... finalStates) {
		ImmutableSet<AtomStatus> finalStatesSet = ImmutableSet.<AtomStatus>builder()
			.addAll(Arrays.asList(finalStates))
			.build();

		this.observers.get(atomNumber - 1)
			.awaitCount(4, TestWaitStrategy.SLEEP_100MS, TIMEOUT_MS)
			.assertSubscribed()
			.assertNoTimeout()
			.assertNoErrors()
			.assertValueAt(0, SubmitAtomRequestAction.class::isInstance)
			.assertValueAt(1, SubmitAtomSendAction.class::isInstance)
			.assertValueAt(2, SubmitAtomReceivedAction.class::isInstance)
			.assertValueAt(3, SubmitAtomStatusAction.class::isInstance)
			.assertValueAt(3, i -> finalStatesSet.contains(SubmitAtomStatusAction.class.cast(i).getStatusNotification().getAtomStatus()));
	}

	private void awaitAtomValidationError(String partMessage) {
		awaitAtomValidationError(this.observers.size(), partMessage);
	}

	private void awaitAtomValidationError(int atomNumber, String partMessage) {
		this.observers.get(atomNumber - 1)
			.awaitCount(4, TestWaitStrategy.SLEEP_100MS, TIMEOUT_MS)
			.assertSubscribed()
			.assertNoTimeout()
			.assertNoErrors()
			.assertValueAt(0, SubmitAtomRequestAction.class::isInstance)
			.assertValueAt(1, SubmitAtomSendAction.class::isInstance)
			.assertValueAt(2, SubmitAtomReceivedAction.class::isInstance)
			.assertValueAt(3, SubmitAtomStatusAction.class::isInstance)
			.assertValueAt(3, i -> SubmitAtomStatusAction.class.cast(i).getStatusNotification().getAtomStatus().equals(AtomStatus.EVICTED_FAILED_CM_VERIFICATION))
			.assertValueAt(3, i -> SubmitAtomStatusAction.class.cast(i).getStatusNotification().getData().getAsJsonObject().has("message"))
			.assertValueAt(3, i -> {
				String message = SubmitAtomStatusAction.class.cast(i).getStatusNotification().getData().getAsJsonObject().get("message").getAsString();
				return message.contains(partMessage);
			});
	}

	private void awaitAtomException(Class<? extends Throwable> exceptionClass, String partialExceptionMessage) {
		awaitAtomException(this.observers.size(), exceptionClass, partialExceptionMessage);
	}

	private void awaitAtomException(int atomNumber, Class<? extends Throwable> exceptionClass, String partialExceptionMessage) {
		this.observers.get(atomNumber - 1)
			.awaitCount(3, TestWaitStrategy.SLEEP_100MS, TIMEOUT_MS)
			.assertError(exceptionClass)
			.assertError(t -> t.getMessage().contains(partialExceptionMessage));
	}
}
