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

package com.radix.acceptance.burn_multi_issuance_tokens;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radix.regression.Util;
import com.radix.test.utils.TokenUtilities;
import com.radixdlt.atom.MutableTokenDefinition;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionsState;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;
import io.reactivex.disposables.Disposable;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.radixdlt.utils.UInt256;

import com.google.common.collect.Lists;
import com.radix.acceptance.SpecificProperties;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.identifiers.RadixAddress;

import static com.radixdlt.client.core.atoms.AtomStatus.STORED;
import static com.radixdlt.client.core.atoms.AtomStatus.EVICTED_FAILED_CM_VERIFICATION;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
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

	private RadixApplicationAPI api;
	private RadixNode nodeConnection;
	private RadixIdentity identity;
	private RadixApplicationAPI otherApix;
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
	private final List<Exception> actionExceptions = Lists.newArrayList();
	private final List<Disposable> disposables = Lists.newArrayList();
	private final UInt256 fee = UInt256.TEN.pow(TokenDefinitionUtils.SUB_UNITS_POW_10 - 3).multiply(UInt256.from(1000));

	@After
	public void cleanUp() {
		disposables.forEach(Disposable::dispose);
	}

	@Given
	("^a library client who owns an account and created token \"([^\"]*)\" with (\\d+) initial supply and is listening to state of \"([^\"]*)\"$")
	public void a_library_client_who_owns_an_account_and_created_token_with_initial_supply_and_is_listening_to_the_state_of(
			String symbol1, int initialSupply, String symbol2) throws Throwable {
		assertEquals(symbol1, symbol2); // Only case we handle at the moment

		setupApi();
		this.properties.put(SYMBOL, symbol1);
		this.properties.put(INITIAL_SUPPLY, Integer.toString(initialSupply));
		createToken();
		awaitAtomStatus(STORED);
		// Listening on state automatic for library
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

	@Given("^a library client who does not own a token class \"([^\"]*)\" on another account with (\\d+) initial supply$")
	public void a_library_client_who_does_not_own_a_token_class_on_another_account(String symbol, int initialSupply) throws Throwable {
		setupApi();
		setupOtherApi();

		this.properties.put(SYMBOL, symbol);
		createToken(this.otherApix);
		awaitAtomStatus(STORED);

		Disposable d = this.api.pull(this.otherApix.getAddress());
		this.api.observeTokenDef(RRI.of(this.otherApix.getAddress(), symbol))
			.firstOrError()
			.timeout(15, TimeUnit.SECONDS)
			.blockingGet();
		d.dispose();

		this.properties.put(ADDRESS, this.api.getAddress().toString());
		this.properties.put(OTHER_ADDRESS, this.otherApix.getAddress().toString());
		this.properties.put(INITIAL_SUPPLY, Integer.toString(initialSupply));
	}

	@When("^the client executes 'BURN (\\d+) \"([^\"]*)\" tokens' on the other account$")
	public void the_client_executes_burn_tokens_on_the_other_account(int newSupply, String symbol) throws Throwable {
		burnTokens(
			BigDecimal.valueOf(newSupply),
			symbol,
			RadixAddress.from(this.properties.get(OTHER_ADDRESS)),
			RadixAddress.from(this.properties.get(OTHER_ADDRESS))
		);
	}

	@When("^the client executes 'BURN (\\d+) \"([^\"]*)\" tokens'$")
	public void the_client_executes_burn_tokens(int newSupply, String symbol) throws Throwable {
		burnTokens(
			BigDecimal.valueOf(newSupply),
			symbol,
			RadixAddress.from(this.properties.get(ADDRESS)),
			RadixAddress.from(this.properties.get(ADDRESS))
		);
	}

	@When("^the client waits to be notified that \"([^\"]*)\" token has a total supply of (\\d+)$")
	public void theClientWaitsToBeNotifiedThatTokenHasATotalSupplyOf(String symbol, int supply) throws Throwable {
		awaitAtomStatus(STORED);
		RRI tokenClass = RRI.of(api.getAddress(), symbol);
		// Ensure balance is up-to-date.
		BigDecimal tokenBalanceDecimal = api.observeBalance(api.getAddress(), tokenClass)
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
		RRI tokenClass = RRI.of(api.getAddress(), symbol);
		// Ensure balance is up-to-date.
		BigDecimal tokenBalanceDecimal = api.observeBalance(api.getAddress(), tokenClass)
			.firstOrError()
			.blockingGet();
		UInt256 tokenBalance = TokenUnitConversions.unitsToSubunits(tokenBalanceDecimal);
		UInt256 requiredBalance = TokenUnitConversions.unitsToSubunits(supply);
		assertEquals(requiredBalance, tokenBalance);
	}

	@Then("^the client should be notified that the action failed because \"([^\"]*)\" does not exist$")
	public void the_client_should_be_notified_that_the_action_failed_because_does_not_exist(String arg1) throws Throwable {
		assertThat(actionExceptions.get(0)).isExactlyInstanceOf(TxBuilderException.class);
	}

	@Then("^the client should be notified that the action failed because there's not that many tokens in supply$")
	public void the_client_should_be_notified_that_the_action_failed_because_there_s_not_that_many_tokens_in_supply() throws Throwable {
		assertThat(actionExceptions.get(0)).isExactlyInstanceOf(TxBuilderException.class);
	}

	@Then("^the client should be notified that the action failed because the client does not have permission to burn those tokens$")
	public void the_client_should_be_notified_that_the_action_failed_because_the_client_does_not_have_permission()
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
		this.otherApix = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), this.otherIdentity);
		TokenUtilities.requestTokensFor(this.otherApix);
		this.disposables.add(this.otherApix.pull());
	}

	private void createToken() throws TxBuilderException {
		createToken(this.api);
	}

	private void createToken(RadixApplicationAPI api) throws TxBuilderException {
		TestObserver<SubmitAtomAction> observer = new TestObserver<>();
		var particles = api.getAtomStore().getUpParticles(api.getAddress(), null).collect(Collectors.toList());
		var builder = TxBuilder.newBuilder(api.getAddress(), particles)
			.createMutableToken(new MutableTokenDefinition(
				this.properties.get(SYMBOL),
				this.properties.get(NAME),
				this.properties.get(DESCRIPTION),
				null,
				null,
				ImmutableMap.of(
					MutableSupplyTokenDefinitionParticle.TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY,
					MutableSupplyTokenDefinitionParticle.TokenTransition.BURN, TokenPermission.TOKEN_OWNER_ONLY
				)
			))
			.burnForFee(api.getNativeTokenRef(), fee);
		var atom = api.getIdentity().addSignature(builder.toLowLevelBuilder()).blockingGet();
		api.submitAtom(atom)
			.toObservable()
			.doOnNext(System.out::println)
			.subscribe(observer);
		observers.add(observer);
	}

	private void transferTokens(BigDecimal amount, String symbol, RadixAddress address) {
		RRI rri = RRI.of(address, symbol);
		TestObserver<SubmitAtomAction> observer = new TestObserver<>(Util.loggingObserver("Transfer Tokens"));

		api.pullOnce(address).blockingAwait();
		var particles = api.getAtomStore().getUpParticles(api.getAddress(), null).collect(Collectors.toList());
		try {
			var builder = TxBuilder.newBuilder(api.getAddress(), particles)
				.transfer(rri, address, TokenUnitConversions.unitsToSubunits(amount))
				.burnForFee(api.getNativeTokenRef(), fee);

			var atom = api.getIdentity().addSignature(builder.toLowLevelBuilder()).blockingGet();
			api.submitAtom(atom).toObservable().subscribe(observer);
			this.observers.add(observer);
		} catch (TxBuilderException e) {
			this.actionExceptions.add(e);
		}
	}

	private void burnTokens(BigDecimal amount, String symbol, RadixAddress tokenAddress, RadixAddress address) {
		RRI rri = RRI.of(tokenAddress, symbol);
		TestObserver<SubmitAtomAction> observer = new TestObserver<>(Util.loggingObserver("Transfer Tokens"));

		api.pullOnce(address).blockingAwait();
		var particles = api.getAtomStore().getUpParticles(api.getAddress(), null).collect(Collectors.toList());
		try {
			var builder = TxBuilder.newBuilder(api.getAddress(), particles)
				.burn(rri, TokenUnitConversions.unitsToSubunits(amount))
				.burnForFee(api.getNativeTokenRef(), fee);

			var atom = api.getIdentity().addSignature(builder.toLowLevelBuilder()).blockingGet();
			api.submitAtom(atom).toObservable().subscribe(observer);
			this.observers.add(observer);
		} catch (TxBuilderException e) {
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
