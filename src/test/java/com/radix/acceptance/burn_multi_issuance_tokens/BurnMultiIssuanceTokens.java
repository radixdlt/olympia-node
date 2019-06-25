package com.radix.acceptance.burn_multi_issuance_tokens;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.application.translate.StageActionException;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionsState;
import com.radixdlt.client.application.translate.tokens.TokenUnitConversions;
import com.radixdlt.client.application.translate.tokens.TransferTokensAction;
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
import com.radixdlt.client.application.translate.tokens.BurnTokensAction;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.application.translate.tokens.InsufficientFundsException;
import com.radixdlt.client.application.translate.tokens.UnknownTokenException;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.Bootstrap;

import static com.radixdlt.client.core.atoms.AtomStatus.STORED;
import static com.radixdlt.client.core.atoms.AtomStatus.EVICTED_FAILED_CM_VERIFICATION;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import io.reactivex.observers.BaseTestConsumer.TestWaitStrategy;
import io.reactivex.observers.TestObserver;

/**
 * See <a href="https://radixdlt.atlassian.net/browse/RLAU-95">RLAU-95</a>.
 */
public class BurnMultiIssuanceTokens {
	private static final String ADDRESS = "address";
	private static final String OTHER_ADDRESS = "otherAddress";
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
		OTHER_ADDRESS,  "unknown",
		NAME,           "RLAU-40 Test token",
		SYMBOL,			"RLAU",
		DESCRIPTION,	"RLAU-40 Test token",
		INITIAL_SUPPLY,	"1000000000",
		NEW_SUPPLY,		"1000000000",
		GRANULARITY,	"1"
	);
	private final List<TestObserver<SubmitAtomAction>> observers = Lists.newArrayList();
	private final List<StageActionException> actionExceptions = Lists.newArrayList();

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

	@Given("^a library client who owns an account where token \"([^\"]*)\" does not exist$")
	public void a_library_client_who_owns_an_account_where_token_does_not_exist(String symbol) throws Throwable {
		setupApi();
		// No tokens exist for this account, because it is a freshly created account
		RRI tokenClass = RRI.of(api.getMyAddress(), symbol);
		TokenDefinitionsState tokenClassesState = api.getTokenDefs()
			.firstOrError()
			.blockingGet();
		assertFalse(tokenClassesState.getState().containsKey(tokenClass));
	}

	@Given("^a library client who does not own a token class \"([^\"]*)\" on another account with (\\d+) initial supply$")
	public void a_library_client_who_does_not_own_a_token_class_on_another_account(String symbol, int initialSupply) throws Throwable {
		setupApi();

		this.properties.put(SYMBOL, symbol);
		createToken(this.otherApi, TokenSupplyType.MUTABLE);
		awaitAtomStatus(STORED);

		this.properties.put(ADDRESS, this.api.getMyAddress().toString());
		this.properties.put(OTHER_ADDRESS, this.otherApi.getMyAddress().toString());
		this.properties.put(INITIAL_SUPPLY, Integer.toString(initialSupply));
	}

	@When("^the client executes 'BURN (\\d+) \"([^\"]*)\" tokens' on the other account$")
	public void the_client_executes_burn_tokens_on_the_other_account(int newSupply, String symbol) throws Throwable {
		burnTokens(BigDecimal.valueOf(newSupply), symbol, RadixAddress.from(this.properties.get(OTHER_ADDRESS)), RadixAddress.from(this.properties.get(OTHER_ADDRESS)));
	}

	@When("^the client executes 'BURN (\\d+) \"([^\"]*)\" tokens'$")
	public void the_client_executes_burn_tokens(int newSupply, String symbol) throws Throwable {
		burnTokens(BigDecimal.valueOf(newSupply), symbol, RadixAddress.from(this.properties.get(ADDRESS)), RadixAddress.from(this.properties.get(ADDRESS)));
	}

	@When("^the client waits to be notified that \"([^\"]*)\" token has a total supply of (\\d+)$")
	public void theClientWaitsToBeNotifiedThatTokenHasATotalSupplyOf(String symbol, int supply) throws Throwable {
		awaitAtomStatus(STORED);
		RRI tokenClass = RRI.of(api.getMyAddress(), symbol);
		// Ensure balance is up-to-date.
		BigDecimal tokenBalanceDecimal = api.getBalance(api.getMyAddress(), tokenClass)
			.firstOrError()
			.blockingGet();
		UInt256 tokenBalance = TokenUnitConversions.unitsToSubunits(tokenBalanceDecimal);
		UInt256 requiredBalance = TokenUnitConversions.unitsToSubunits(supply);
		assertEquals(requiredBalance, tokenBalance);
	}

	@When("^the client executes 'TRANSFER (\\d+) \"([^\"]*)\" tokens' to himself$")
	public void the_client_executes_token_transfer_to_self(int amount, String symbol) throws Throwable {
		transferTokens(BigDecimal.valueOf(amount), symbol, RadixAddress.from(this.properties.get(ADDRESS)));
	}

	@Then("^the client should be notified that \"([^\"]*)\" token has a total supply of (\\d+)$")
	public void theClientShouldBeNotifiedThatTokenHasATotalSupplyOf(String symbol, int supply) throws Throwable {
		awaitAtomStatus(STORED);
		// Must be a better way than this.
		RRI tokenClass = RRI.of(api.getMyAddress(), symbol);
		// Ensure balance is up-to-date.
		BigDecimal tokenBalanceDecimal = api.getBalance(api.getMyAddress(), tokenClass)
			.firstOrError()
			.blockingGet();
		UInt256 tokenBalance = TokenUnitConversions.unitsToSubunits(tokenBalanceDecimal);
		UInt256 requiredBalance = TokenUnitConversions.unitsToSubunits(supply);
		assertEquals(requiredBalance, tokenBalance);
	}

	@Then("^the client should be notified that the action failed because \"([^\"]*)\" does not exist$")
	public void the_client_should_be_notified_that_the_action_failed_because_does_not_exist(String arg1) throws Throwable {
		assertThat(actionExceptions.get(0)).isExactlyInstanceOf(UnknownTokenException.class);
	}

	@Then("^the client should be notified that the action failed because there's not that many tokens in supply$")
	public void the_client_should_be_notified_that_the_action_failed_because_there_s_not_that_many_tokens_in_supply() throws Throwable {
		assertThat(actionExceptions.get(0)).isExactlyInstanceOf(InsufficientFundsException.class);
	}

	@Then("^the client should be notified that the action failed because the client does not have permission to burn those tokens$")
	public void the_client_should_be_notified_that_the_action_failed_because_the_client_does_not_have_permission_to_burn_those_tokens() throws Throwable {
		awaitAtomValidationError("signed");
	}

	private void setupApi() {
		this.identity = RadixIdentities.createNew();
		this.api = RadixApplicationAPI.create(Bootstrap.LOCALHOST_SINGLENODE, this.identity);
		this.api.pull();

		this.otherIdentity = RadixIdentities.createNew();
		this.otherApi = RadixApplicationAPI.create(Bootstrap.LOCALHOST_SINGLENODE, this.otherIdentity);
		this.otherApi.pull();

		// Reset data
		this.properties.clear();
		this.observers.clear();
		this.actionExceptions.clear();

		this.properties.put(ADDRESS, api.getMyAddress().toString());
	}

	private void createToken(CreateTokenAction.TokenSupplyType tokenCreateSupplyType) {
		createToken(this.api, tokenCreateSupplyType);
	}

	private void createToken(RadixApplicationAPI api, CreateTokenAction.TokenSupplyType tokenCreateSupplyType) {
		TestObserver<SubmitAtomAction> observer = new TestObserver<>();
		api.createToken(
			RRI.of(api.getMyAddress(), this.properties.get(SYMBOL)),
				this.properties.get(NAME),
				this.properties.get(DESCRIPTION),
				BigDecimal.valueOf(Long.valueOf(this.properties.get(INITIAL_SUPPLY))),
				BigDecimal.valueOf(Long.valueOf(this.properties.get(GRANULARITY))),
				tokenCreateSupplyType)
			.toObservable()
			.doOnNext(System.out::println)
			.subscribe(observer);
		this.observers.add(observer);
	}

	private void transferTokens(BigDecimal amount, String symbol, RadixAddress address) {
		RRI tokenClass = RRI.of(address, symbol);
		TransferTokensAction tta = TransferTokensAction.create(address, address, amount, tokenClass);
		TestObserver<SubmitAtomAction> observer = new TestObserver<>();
		api.pullOnce(address).blockingAwait();
		try {
			api.execute(tta).toObservable().doOnNext(System.out::println).subscribe(observer);
			this.observers.add(observer);
		} catch (StageActionException e) {
			this.actionExceptions.add(e);
		}
	}

	private void burnTokens(BigDecimal amount, String symbol, RadixAddress tokenAddress, RadixAddress address) {
		RRI tokenClass = RRI.of(tokenAddress, symbol);
		BurnTokensAction mta = BurnTokensAction.create(address, tokenClass, amount);
		TestObserver<SubmitAtomAction> observer = new TestObserver<>();
		api.pullOnce(tokenAddress).blockingAwait();
		try {
			api.execute(mta).toObservable().doOnNext(System.out::println).subscribe(observer);
			this.observers.add(observer);
			observer.awaitTerminalEvent();
		} catch (StageActionException e) {
			this.actionExceptions.add(e);
		}
	}

	private void awaitAtomStatus(AtomStatus... finalStates) {
		awaitAtomStatus(this.observers.size(), finalStates);
	}


	private void awaitAtomStatus(int atomNumber, AtomStatus... finalStates) {
		ImmutableSet<AtomStatus> finalStatesSet = ImmutableSet.<AtomStatus>builder()
			.addAll(Arrays.asList(finalStates))
			.build();

		TestObserver<SubmitAtomAction> testObserver = this.observers.get(atomNumber - 1);
		testObserver.awaitTerminalEvent();
		testObserver.assertNoErrors();
		testObserver.assertNoTimeout();
		List<SubmitAtomAction> events = testObserver.values();
		assertThat(events).extracting(o -> o.getClass().toString())
			.startsWith(
				SubmitAtomRequestAction.class.toString(),
				SubmitAtomSendAction.class.toString()
			);
		assertThat(events).last()
			.isInstanceOf(SubmitAtomStatusAction.class)
			.<AtomStatus>extracting(o -> SubmitAtomStatusAction.class.cast(o).getStatusNotification().getAtomStatus())
			.isIn(finalStatesSet);
	}

	private void awaitAtomValidationError(String partMessage) {
		awaitAtomValidationError(this.observers.size(), partMessage);
	}

	private void awaitAtomValidationError(int atomNumber, String partMessage) {
		TestObserver<SubmitAtomAction> testObserver = this.observers.get(atomNumber - 1);
		testObserver.awaitTerminalEvent();
		testObserver.assertNoErrors();
		testObserver.assertNoTimeout();
		List<SubmitAtomAction> events = testObserver.values();
		assertThat(events).extracting(o -> o.getClass().toString())
			.startsWith(
				SubmitAtomRequestAction.class.toString(),
				SubmitAtomSendAction.class.toString()
			);
		assertThat(events).last()
			.isInstanceOf(SubmitAtomStatusAction.class)
			.satisfies(s -> {
				SubmitAtomStatusAction action = SubmitAtomStatusAction.class.cast(s);
				assertThat(action.getStatusNotification().getAtomStatus()).isEqualTo(EVICTED_FAILED_CM_VERIFICATION);
				assertThat(action.getStatusNotification().getData().getAsJsonObject().has("message")).isTrue();
				assertThat(action.getStatusNotification().getData().getAsJsonObject().get("message").getAsString()).contains(partMessage);
			});
	}

	private void awaitAtomException(Class<? extends Throwable> exceptionClass, String partialExceptionMessage) {
		awaitAtomException(this.observers.size(), exceptionClass, partialExceptionMessage);
	}

	private void awaitAtomException(int atomNumber, Class<? extends Throwable> exceptionClass, String partialExceptionMessage) {
		this.observers.get(atomNumber - 1)
			.awaitCount(3, TestWaitStrategy.SLEEP_100MS, TIMEOUT_MS)
			.assertError(exceptionClass)
			.assertError(t -> t.getMessage().toLowerCase().contains(partialExceptionMessage.toLowerCase()));
	}
}
