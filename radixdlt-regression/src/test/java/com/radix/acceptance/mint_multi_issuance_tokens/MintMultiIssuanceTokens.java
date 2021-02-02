/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radix.acceptance.mint_multi_issuance_tokens;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.application.RadixApplicationAPI.Transaction;
import com.radixdlt.client.application.translate.StageActionException;
import com.radixdlt.client.application.translate.tokens.TokenOverMintException;
import com.radixdlt.client.application.translate.tokens.TokenUnitConversions;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;
import io.cucumber.java.After;
import io.reactivex.disposables.Disposable;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import java.util.concurrent.TimeUnit;
import com.radixdlt.utils.UInt256;

import com.google.common.collect.Lists;
import com.radix.acceptance.SpecificProperties;
import com.radix.test.utils.TokenUtilities;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.application.translate.tokens.MintTokensAction;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionsState;
import com.radixdlt.client.application.translate.tokens.UnknownTokenException;
import com.radixdlt.identifiers.RadixAddress;

import static com.radixdlt.client.core.atoms.AtomStatus.EVICTED_FAILED_CM_VERIFICATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
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

	private RadixApplicationAPI api;
	private RadixIdentity identity;
	private RadixApplicationAPI otherApi;
	private RadixIdentity otherIdentity;

	private RadixNode nodeConnection;
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
	private final List<Disposable> disposables = Lists.newArrayList();
	private final List<StageActionException> actionExceptions = Lists.newArrayList();
	private final List<Exception> otherExceptions = Lists.newArrayList();

	@After
	public void cleanUp() {
		disposables.forEach(Disposable::dispose);
	}

	@Given("^a library client who owns an account and created token \"([^\"]*)\" with (\\d+) initial supply "
	       + "and is listening to the state of \"([^\"]*)\"$")
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

	@Given("^a library client who owns an account and created a token with 2\\^(\\d+) initial subunit supply "
	       + "and is listening to the state of the token$")
	public void a_library_client_who_owns_an_account_and_created_a_token_with_initial_subunit_supply_and_is_listening(int pow2)
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
		RRI tokenClass = RRI.of(api.getAddress(), symbol);
		TokenDefinitionsState tokenClassesState = api.observeTokenDefs()
			.firstOrError()
			.blockingGet();
		assertFalse(tokenClassesState.getState().containsKey(tokenClass));
	}

	@Given("^a library client who does not own a token class \"([^\"]*)\" on another account$")
	public void a_library_client_who_does_not_own_a_token_class_on_another_account(String symbol) throws Throwable {
		setupApi();
		setupOtherApi();

		Disposable d = this.api.pull(this.otherApi.getAddress());
		this.properties.put(SYMBOL, symbol);
		createToken(this.otherApi, TokenSupplyType.MUTABLE);
		awaitAtomStatus(AtomStatus.STORED);
		this.api.observeTokenDef(RRI.of(this.otherApi.getAddress(), symbol))
			.timeout(15, TimeUnit.SECONDS)
			.firstOrError()
			.blockingGet();
		d.dispose();

		this.properties.put(ADDRESS, this.otherApi.getAddress().toString());
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
		mintTokens(
			BigDecimal.valueOf(2).pow(pow2).scaleByPowerOfTen(-18),
			this.properties.get(SYMBOL),
			RadixAddress.from(this.properties.get(ADDRESS))
		);
	}

	@Then("^the client should be notified that \"([^\"]*)\" token has a total supply of (\\d+)$")
	public void theClientShouldBeNotifiedThatTokenHasATotalSupplyOf(String symbol, int supply) throws Throwable {
		awaitAtomStatus(AtomStatus.STORED);
		RRI tokenClass = RRI.of(api.getAddress(), symbol);
		// Ensure balance is up-to-date.
		BigDecimal tokenBalanceDecimal = api.observeBalance(api.getAddress(), tokenClass)
			.firstOrError()
			.blockingGet();
		UInt256 tokenBalance = TokenUnitConversions.unitsToSubunits(tokenBalanceDecimal);
		UInt256 requiredBalance = TokenUnitConversions.unitsToSubunits(supply);
		assertEquals(requiredBalance, tokenBalance);
	}

	@Then("^the client should be notified that the action failed because cannot mint with (\\d+) tokens$")
	public void the_client_should_be_notified_that_the_action_failed_because_cannot_mint_with_tokens(int count) throws Throwable {
		assertEquals(0, count); // Only thing we check for here
		assertThat(otherExceptions.get(0)).isInstanceOf(IllegalArgumentException.class);
		assertThat(otherExceptions.get(0).getMessage()).contains("Mint amount must be greater than 0.");
	}

	@Then("^the client should be notified that the action failed because it reached the max allowed number of tokens of 2\\^256 - 1$")
	public void the_client_should_be_notified_that_the_action_failed_because_it_reached_the_max_allowed_number_of_tokens_of() throws Throwable {
		assertThat(actionExceptions.get(0)).isInstanceOf(TokenOverMintException.class);
		assertThat(actionExceptions.get(0).getMessage()).contains("would overflow maximum");
	}

	@Then("^the client should be notified that the action failed because \"([^\"]*)\" does not exist$")
	public void the_client_should_be_notified_that_the_action_failed_because_does_not_exist(String arg1) throws Throwable {
		assertThat(actionExceptions.get(0)).isInstanceOf(UnknownTokenException.class);
	}

	@Then("^the client should be notified that the action failed because the client does not have permission to mint those tokens$")
	public void the_client_should_be_notified_that_the_action_failed_because_the_client_does_not_have_permission_to_mint_those_tokens()
		throws Throwable {
		awaitAtomValidationError();
	}

	private void setupApi() {
		this.identity = RadixIdentities.createNew();
		this.api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), this.identity);
		TokenUtilities.requestTokensFor(this.api);
		this.disposables.add(this.api.pull());

		// Reset data
		this.properties.clear();
		this.observers.clear();
		this.actionExceptions.clear();
		this.otherExceptions.clear();

		this.properties.put(ADDRESS, api.getAddress().toString());


		this.api.discoverNodes();
		this.nodeConnection = this.api.getNetworkState()
			.map(RadixNetworkState::getNodes)
			.filter(s -> !s.isEmpty())
			.map(s -> s.iterator().next())
			.firstOrError()
			.blockingGet();
	}

	private void setupOtherApi() {
		this.otherIdentity = RadixIdentities.createNew();
		this.otherApi = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), this.otherIdentity);
		TokenUtilities.requestTokensFor(this.otherApi);
		this.disposables.add(this.otherApi.pull());
	}

	private void createToken(CreateTokenAction.TokenSupplyType tokenCreateSupplyType) {
		createToken(this.api, tokenCreateSupplyType);
	}

	private void createToken(RadixApplicationAPI api, CreateTokenAction.TokenSupplyType tokenCreateSupplyType) {
		TestObserver<SubmitAtomAction> observer = new TestObserver<>();
		CreateTokenAction createTokenAction = CreateTokenAction.create(
			RRI.of(api.getAddress(), this.properties.get(SYMBOL)),
			this.properties.get(NAME),
			this.properties.get(DESCRIPTION),
			new BigDecimal(this.properties.get(INITIAL_SUPPLY)),
			new BigDecimal(this.properties.get(GRANULARITY)),
			tokenCreateSupplyType
		);
		Transaction tx = api.createTransaction();
		tx.stage(createTokenAction);
		tx.commitAndPush(nodeConnection)
			.toObservable()
			.doOnNext(System.out::println)
			.subscribe(observer);
		this.observers.add(observer);
	}

	private void mintTokens(BigDecimal amount, String symbol, RadixAddress address) {
		RRI tokenClass = RRI.of(address, symbol);
		MintTokensAction mta = MintTokensAction.create(tokenClass, address, amount);
		TestObserver<SubmitAtomAction> observer = new TestObserver<>();
		api.pullOnce(address).blockingAwait();
		try {
			Transaction tx = api.createTransaction();
			tx.stage(mta);
			tx.commitAndPush(nodeConnection)
				.toObservable().doOnNext(System.out::println).subscribe(observer);
			this.observers.add(observer);
		} catch (StageActionException e) {
			actionExceptions.add(e);
		} catch (Exception e) {
			otherExceptions.add(e);
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
			.startsWith(SubmitAtomSendAction.class.toString());
		assertThat(events).last()
			.isInstanceOf(SubmitAtomStatusAction.class)
			.extracting(o -> SubmitAtomStatusAction.class.cast(o).getStatusNotification().getAtomStatus())
			.isIn(finalStatesSet);
	}

	private void awaitAtomValidationError() {
		awaitAtomValidationError(this.observers.size());
	}

	private void awaitAtomValidationError(int atomNumber) {
		assertThat(actionExceptions).isEmpty();
		assertThat(otherExceptions).isEmpty();

		TestObserver<SubmitAtomAction> testObserver = this.observers.get(atomNumber - 1);
		testObserver.awaitTerminalEvent();
		testObserver.assertNoErrors();
		testObserver.assertNoTimeout();
		List<SubmitAtomAction> events = testObserver.values();
		assertThat(events).extracting(o -> o.getClass().toString())
			.startsWith(SubmitAtomSendAction.class.toString());
		assertThat(events).last()
			.isInstanceOf(SubmitAtomStatusAction.class)
			.satisfies(s -> {
				SubmitAtomStatusAction action = SubmitAtomStatusAction.class.cast(s);
				assertThat(action.getStatusNotification().getAtomStatus()).isEqualTo(EVICTED_FAILED_CM_VERIFICATION);
				assertThat(action.getStatusNotification().getData().getAsJsonObject().has("message")).isTrue();
			});
	}
}
