package com.radix.acceptance.mint_multi_issuance_tokens;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.radix.utils.UInt256;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.radix.acceptance.SpecificProperties;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.tokenclasses.CreateTokenAction;
import com.radixdlt.client.application.translate.tokenclasses.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.application.translate.tokenclasses.MintTokensAction;
import com.radixdlt.client.application.translate.tokenclasses.TokenClassesState;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.network.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState;

import static com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState.STORED;
import static com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState.SUBMITTED;
import static com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState.SUBMITTING;
import static com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState.VALIDATION_ERROR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import cucumber.api.PendingException;
import cucumber.api.java.After;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.BaseTestConsumer.TestWaitStrategy;
import io.reactivex.observers.TestObserver;

/**
 * See <a href="https://radixdlt.atlassian.net/browse/RLAU-93">RLAU-93</a>.
 */
public class MintMultiIssuanceTokens {
	static {
		if (!RadixUniverse.isInstantiated()) {
			RadixUniverse.bootstrap(Bootstrap.BETANET);
		}
	}

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
		INITIAL_SUPPLY,	scaledToUnscaled(1000000000).toString(),
		NEW_SUPPLY,		scaledToUnscaled(1000000000).toString(),
		GRANULARITY,	"1"
	);
	private final List<TestObserver<Object>> observers = Lists.newArrayList();
	private final List<Disposable> disposables = Lists.newArrayList();

	@After
	public void after() {
		this.disposables.forEach(Disposable::dispose);
		this.disposables.clear();
	}

	@Given("^a library client who owns an account and created token \"([^\"]*)\" with (\\d+) initial supply and is listening to the state of \"([^\"]*)\"$")
	public void a_library_client_who_owns_an_account_and_created_token_with_initial_supply_and_is_listening_to_the_state_of(
			String symbol1, int initialSupply, String symbol2) throws Throwable {
		assertEquals(symbol1, symbol2); // Only case we handle at the moment

		setupApi();
		this.properties.put(SYMBOL, symbol1);
		this.properties.put(INITIAL_SUPPLY, Integer.toString(initialSupply));
		createToken(TokenSupplyType.MUTABLE);
		awaitAtomStatus(STORED);
		// Listening on state automatic for library
	}

	@Given("^a library client who owns an account and created token \"([^\"]*)\" with (\\d+) initial supply and is listening to the token class actions of this token$")
	public void a_library_client_who_owns_an_account_and_created_token_with_initial_supply_and_is_listening_to_the_token_class_actions_of_this_token(
			String symbol, int initialSupply) throws Throwable {
		setupApi();

		this.properties.put(SYMBOL, symbol);
		this.properties.put(INITIAL_SUPPLY, scaledToUnscaled(initialSupply).toString());
		createToken(TokenSupplyType.MUTABLE);
		awaitAtomStatus(STORED);

	    // FIXME Write code here that turns the phrase above into concrete actions
		// The "listening to the token class actions" part is missing
	    throw new PendingException();
	}

	@Given("^a library client and a token \"([^\"]*)\" which has (\\d+) total supply from three 'MINT (\\d+)' actions$")
	public void aLibraryClientAndATokenWhichHasTotalSupplyFromThreeMINTActions(String symbol, int totalSupply, int perMint)
			throws Throwable {
		assertEquals(totalSupply, 3 * perMint); // Required from wording

		setupApi();
		this.properties.put(SYMBOL, symbol);
		this.properties.put(INITIAL_SUPPLY, "0");
		createToken(CreateTokenAction.TokenSupplyType.MUTABLE);
		awaitAtomStatus(STORED);
		for (int i = 0; i < 3; ++i) {
			mintTokens(scaledToUnscaled(perMint), symbol, RadixAddress.from(this.properties.get(ADDRESS)));
			awaitAtomStatus(STORED);
		}
		TokenClassReference tokenClass = TokenClassReference.of(api.getMyAddress(), symbol);
		// Ensure balance is up-to-date.
		BigDecimal tokenBalanceDecimal = api.getBalance(api.getMyAddress(), tokenClass)
			.firstOrError()
			.blockingGet();
		UInt256 tokenBalance = TokenClassReference.unitsToSubunits(tokenBalanceDecimal);
		UInt256 requiredBalance = TokenClassReference.unitsToSubunits(totalSupply);
		assertEquals(requiredBalance, tokenBalance);
	}

	@Given("^a library client who owns an account and created a token with (\\d+) initial subunit supply$")
	public void a_library_client_who_owns_an_account_and_created_a_token_with_initial_subunit_supply(int initialUnscaledSupply) throws Throwable {
		setupApi();
		this.properties.put(INITIAL_SUPPLY, UInt256.from(initialUnscaledSupply).toString());
		createToken(CreateTokenAction.TokenSupplyType.MUTABLE);
		awaitAtomStatus(STORED);
	}

	@Given("^a library client who owns an account and created a token with 2\\^(\\d+) initial subunit supply and is listening to the state of the token$")
	public void a_library_client_who_owns_an_account_and_created_a_token_with_initial_subunit_supply_and_is_listening_to_the_state_of_the_token(int pow2)
			throws Throwable {
		setupApi();
		this.properties.put(INITIAL_SUPPLY, UInt256.TWO.pow(pow2).toString());
		createToken(CreateTokenAction.TokenSupplyType.MUTABLE);
		awaitAtomStatus(STORED);
	}

	@Given("^a library client who owns an account where token \"([^\"]*)\" does not exist$")
	public void a_library_client_who_owns_an_account_where_token_does_not_exist(String symbol) throws Throwable {
		setupApi();
		// No tokens exist for this account, because it is a freshly created account
		TokenClassReference tokenClass = TokenClassReference.of(api.getMyAddress(), symbol);
		TokenClassesState tokenClassesState = api.getMyTokenClasses()
			.firstOrError()
			.blockingGet();
		assertFalse(tokenClassesState.getState().containsKey(tokenClass));
	}

	@Given("^a library client who does not own a token class \"([^\"]*)\" on another account$")
	public void a_library_client_who_does_not_own_a_token_class_on_another_account(String symbol) throws Throwable {
		setupApi();

		this.properties.put(SYMBOL, symbol);
		createToken(this.otherApi, TokenSupplyType.MUTABLE);
		awaitAtomStatus(STORED);

		this.properties.put(ADDRESS, this.otherApi.getMyAddress().toString());
	}

	@When("^the client executes mint (\\d+) \"([^\"]*)\" tokens$")
	public void the_client_executes_mint_tokens(int newSupply, String symbol) throws Throwable {
		mintTokens(scaledToUnscaled(newSupply), symbol, RadixAddress.from(this.properties.get(ADDRESS)));
	}

	@When("^the client queries the actions done in token \"([^\"]*)\"$")
	public void the_client_queries_the_actions_done_in_token(String symbol) throws Throwable {
		// FIXME Write code here that turns the phrase above into concrete actions
		throw new PendingException();
	}

	@When("^the client executes mint (\\d+) tokens$")
	public void the_client_executes_mint_tokens(int newSupply) throws Throwable {
		mintTokens(scaledToUnscaled(newSupply), this.properties.get(SYMBOL), RadixAddress.from(this.properties.get(ADDRESS)));
	}

	@When("^the client executes mint 2\\^(\\d+) subunit tokens$")
	public void the_client_executes_mint_subunit_tokens(int pow2) throws Throwable {
		mintTokens(UInt256.TWO.pow(pow2), this.properties.get(SYMBOL), RadixAddress.from(this.properties.get(ADDRESS)));
	}

	@Then("^the client should be notified that \"([^\"]*)\" token has a total supply of (\\d+)$")
	public void theClientShouldBeNotifiedThatTokenHasATotalSupplyOf(String symbol, int supply) throws Throwable {
		awaitAtomStatus(STORED);
		TokenClassReference tokenClass = TokenClassReference.of(api.getMyAddress(), symbol);
		// Ensure balance is up-to-date.
		BigDecimal tokenBalanceDecimal = api.getBalance(api.getMyAddress(), tokenClass)
			.firstOrError()
			.blockingGet();
		UInt256 tokenBalance = TokenClassReference.unitsToSubunits(tokenBalanceDecimal);
		UInt256 requiredBalance = TokenClassReference.unitsToSubunits(supply);
		assertEquals(requiredBalance, tokenBalance);
	}

	@Then("^the client should be notified that a new action of mint (\\d+) \"([^\"]*)\" tokens has been executed$")
	public void the_client_should_be_notified_that_a_new_action_of_mint_tokens_has_been_executed(int amount, String symbol) throws Throwable {
	    // FIXME Write code here that turns the phrase above into concrete actions
	    throw new PendingException();
	}

	@Then("^the client should receive three 'MINT (\\d+)' actions$")
	public void the_client_should_receive_three_MINT_actions(int amount) throws Throwable {
	    // FIXME Write code here that turns the phrase above into concrete actions
	    throw new PendingException();
	}

	@Then("^the client should be notified that the action failed because cannot mint with (\\d+) tokens$")
	public void the_client_should_be_notified_that_the_action_failed_because_cannot_mint_with_tokens(int count) throws Throwable {
		assertEquals(0, count); // Only thing we check for here
		awaitAtomException(IllegalArgumentException.class, "Amount is zero");
	}

	@Then("^the client should be notified that the action failed because it reached the max allowed number of tokens of 2\\^256 - 1$")
	public void the_client_should_be_notified_that_the_action_failed_because_it_reached_the_max_allowed_number_of_tokens_of() throws Throwable {
		awaitAtomStatus(VALIDATION_ERROR);
	}

	@Then("^the client should be notified that the action failed because \"([^\"]*)\" does not exist$")
	public void the_client_should_be_notified_that_the_action_failed_because_does_not_exist(String arg1) throws Throwable {
		awaitAtomException(IllegalArgumentException.class, "Unknown token");
	}

	@Then("^the client should be notified that the action failed because the client does not have permission to mint those tokens$")
	public void the_client_should_be_notified_that_the_action_failed_because_the_client_does_not_have_permission_to_mint_those_tokens() throws Throwable {
		awaitAtomStatus(VALIDATION_ERROR);
	}

	private void setupApi() {
		this.identity = RadixIdentities.createNew();
		this.api = RadixApplicationAPI.create(this.identity);
		this.disposables.add(this.api.pull());

		this.otherIdentity = RadixIdentities.createNew();
		this.otherApi = RadixApplicationAPI.create(this.otherIdentity);
		this.disposables.add(this.otherApi.pull());

		// Reset data
		this.properties.clear();
		this.observers.clear();

		this.properties.put(ADDRESS, api.getMyAddress().toString());
	}

	private void createToken(CreateTokenAction.TokenSupplyType tokenCreateSupplyType) {
		createToken(this.api, tokenCreateSupplyType);
	}

	private void createToken(RadixApplicationAPI api, CreateTokenAction.TokenSupplyType tokenCreateSupplyType) {
		TestObserver<Object> observer = new TestObserver<>();
		api.createToken(
				this.properties.get(NAME),
				this.properties.get(SYMBOL),
				this.properties.get(DESCRIPTION),
				UInt256.from(this.properties.get(INITIAL_SUPPLY)),
				UInt256.from(this.properties.get(GRANULARITY)),
				tokenCreateSupplyType)
			.toObservable()
			.doOnNext(System.out::println)
			.map(AtomSubmissionUpdate::getState)
			.subscribe(observer);
		this.observers.add(observer);
	}

	private void mintTokens(UInt256 amount, String symbol, RadixAddress address) {
		TokenClassReference tokenClass = TokenClassReference.of(address, symbol);
		MintTokensAction mta = new MintTokensAction(tokenClass, amount);
		TestObserver<Object> observer = new TestObserver<>();
		api.execute(mta)
			.toObservable()
			.doOnNext(System.out::println)
			.map(AtomSubmissionUpdate::getState)
			.subscribe(observer);
		this.observers.add(observer);
	}

	private void awaitAtomStatus(AtomSubmissionState... finalStates) {
		awaitAtomStatus(this.observers.size(), finalStates);
	}

	private void awaitAtomStatus(int atomNumber, AtomSubmissionState... finalStates) {
		ImmutableSet<AtomSubmissionState> allStates = ImmutableSet.<AtomSubmissionState>builder()
			.add(SUBMITTING, SUBMITTED)
			.addAll(Arrays.asList(finalStates))
			.build();
		this.observers.get(atomNumber - 1)
			.awaitCount(3, TestWaitStrategy.SLEEP_100MS, TIMEOUT_MS)
			.assertNoErrors()
			.assertNoTimeout()
			.assertValueSet(allStates);
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

	private static UInt256 scaledToUnscaled(int amount) {
		return TokenClassReference.unitsToSubunits(amount);
	}
}
