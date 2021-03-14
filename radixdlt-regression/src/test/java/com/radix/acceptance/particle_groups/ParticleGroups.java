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

package com.radix.acceptance.particle_groups;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.radix.acceptance.SpecificProperties;
import com.radix.test.utils.TokenUtilities;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Transaction;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ShardedParticleStateId;
import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.client.application.translate.StatelessActionToParticleGroupsMapper;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.application.translate.tokens.InsufficientFundsException;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.client.application.translate.tokens.TransferTokensAction;
import com.radixdlt.client.application.translate.tokens.TransferTokensToParticleGroupsMapper;
import com.radixdlt.client.application.translate.unique.PutUniqueIdAction;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.client.core.network.actions.SubmitAtomRequestAction;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;
import com.radixdlt.utils.UInt256;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.reactivex.observers.TestObserver;
import java.util.Set;
import java.util.stream.Stream;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;

/**
 * See <a href="https://radixdlt.atlassian.net/browse/RLAU-326">RLAU-326</a>.
 * <br>
 * (now it's <a href="https://radixdlt.atlassian.net/browse/RPNV1-341">here</a>)
 */
public class ParticleGroups {
	private static final String NAME = "name";
	private static final String SYMBOL = "symbol";
	private static final String DESCRIPTION = "description";
	private static final String TOTAL_SUPPLY = "totalSupply";
	private static final String GRANULARITY = "granularity";

	private RadixApplicationAPI api;
	private RadixIdentity identity;
	private final SpecificProperties properties1 = SpecificProperties.of(
			NAME,           "RLAU-40 Test token",
			SYMBOL,			"RLAU",
			DESCRIPTION,	"RLAU-40 Test token",
			TOTAL_SUPPLY,	"1000000000",
			GRANULARITY,	"1"
	);
	private final SpecificProperties properties2 = SpecificProperties.of(
			NAME,           "RLAU-40 Test token",
			SYMBOL,			"RLAU",
			DESCRIPTION,	"RLAU-40 Test token",
			TOTAL_SUPPLY,	"1000000000",
			GRANULARITY,	"1"
	);
	private final List<TestObserver<Object>> observers = Lists.newArrayList();

	private class CreateEmptyGroupAction implements Action {

	}
	private class CreateEmptyGroupActionToParticleGroupsMapper implements StatelessActionToParticleGroupsMapper<CreateEmptyGroupAction> {

		@Override
		public List<ParticleGroup> mapToParticleGroups(CreateEmptyGroupAction action) {
			return Collections.singletonList(new ParticleGroup(Collections.emptyList()));
		}
	}

	private class MergeAction implements Action {
		private final Action[] actions;
		private MergeAction(Action... actions) {
			this.actions = actions;
		}
	}

	private class MergeStatefulActionToParticleGroupsMapper<T extends Action> implements StatefulActionToParticleGroupsMapper<MergeAction> {
		private final StatefulActionToParticleGroupsMapper<T>[] mappers;
		private MergeStatefulActionToParticleGroupsMapper(StatefulActionToParticleGroupsMapper<T>... mappers) {
			this.mappers = mappers;
		}

		@Override
		public Set<ShardedParticleStateId> requiredState(MergeAction action) {
			return Arrays.stream(mappers).flatMap(mapper ->
				Arrays.stream(action.actions).flatMap(a -> mapper.requiredState((T) a).stream())
			).collect(Collectors.toSet());
		}

		@Override
		public List<ParticleGroup> mapToParticleGroups(MergeAction mergeAction, Stream<Particle> store) {
			List<Particle> particles = store.collect(Collectors.toList());

			return Arrays.stream(mergeAction.actions)
				.flatMap(a -> Arrays.stream(this.mappers)
					 .flatMap(mapper -> mapper.mapToParticleGroups((T) a, particles.stream()).stream()))
				.collect(Collectors.toList());
		}
	}

	@Given("^I have access to a suitable Radix network$")
	public void i_have_access_to_a_suitable_Radix_network() {
		this.identity = RadixIdentities.createNew();
		StatefulActionToParticleGroupsMapper<TransferTokensAction> mapper = new TransferTokensToParticleGroupsMapper();
		this.api = RadixApplicationAPI.defaultBuilder()
			.bootstrap(RadixEnv.getBootstrapConfig())
			.identity(this.identity)
			.addStatelessParticlesMapper(CreateEmptyGroupAction.class, new CreateEmptyGroupActionToParticleGroupsMapper())
			.addStatefulParticlesMapper(MergeAction.class, new MergeStatefulActionToParticleGroupsMapper<>(mapper))
			.build();

		TokenUtilities.requestTokensFor(this.api);

		// Reset data
		this.properties1.clear();
		this.properties2.clear();
		this.observers.clear();
	}

	@And("^I submit one token transfer requests of (\\d+) for \"([^\"]*)\" and of (\\d+) for \"([^\"]*)\" in one particle group$")
	public void i_submit_one_token_transfer_requests_of_for_and_of_for_in_one_particle_group(
		int amount1,
		String symbol1,
		int amount2,
		String symbol2
	) throws Throwable {
		MergeAction doubleSpendAction = new MergeAction(
			buildTransferTokenAction(symbol1, amount1),
			buildTransferTokenAction(symbol2, amount2)
		);

		createAtomic(doubleSpendAction);
	}

	@When("^I submit a fixed-supply token-creation request "
			+ "with name \"([^\"]*)\", symbol \"([^\"]*)\", totalSupply (\\d+) scaled and granularity (\\d+) scaled$")
	public void i_submit_a_fixed_supply_token_creation_request_with_name_symbol_totalSupply_scaled_and_granularity_scaled(
			String name, String symbol, int totalSupply, int granularity) {
		this.properties1.put(NAME, name);
		this.properties1.put(SYMBOL, symbol);
		this.properties1.put(TOTAL_SUPPLY, Long.toString(totalSupply));
		this.properties1.put(GRANULARITY, Long.toString(granularity));
		createToken(CreateTokenAction.TokenSupplyType.FIXED);
	}

	@When("^I submit two fixed-supply token-creation requests with symbol \"([^\"]*)\" and in separate particle groups$")
	public void i_submit_two_fixed_supply_token_creation_request_with_symbol_totalSupply_scaled_scaled(String symbol) {
		this.properties1.put(SYMBOL, symbol);
		this.properties2.put(SYMBOL, symbol);
		CreateTokenAction.TokenSupplyType tokenSupplyType = CreateTokenAction.TokenSupplyType.FIXED;

		createTwoTokens(tokenSupplyType);
	}

	@When("^I submit two fixed-supply token-creation requests with symbol \"([^\"]*)\" and \"([^\"]*)\" and in separate particle groups$")
	public void i_submit_two_fixed_supply_token_creation_request_with_symbol_and_totalSupply_scaled_scaled(String symbol1, String symbol2) {
		this.properties1.put(SYMBOL, symbol1);
		this.properties2.put(SYMBOL, symbol2);
		CreateTokenAction.TokenSupplyType tokenSupplyType = CreateTokenAction.TokenSupplyType.FIXED;

		createTwoTokens(tokenSupplyType);
	}

	@Then("^Two token transfer requests of (\\d+) for \"([^\"]*)\" in separate particle groups should fail$")
	public void i_submit_two_token_transfer_requests_of_for_in_separate_particle_groups(int amount, String symbol) {
		assertThatThrownBy(() ->
			createAtomic(
				buildTransferTokenAction(symbol, 100L),
				buildTransferTokenAction(symbol, 100L)
			)
		).isInstanceOf(InsufficientFundsException.class);
	}

	@When("^I submit an arbitrary atom with an empty particle group$")
	public void i_submit_an_arbitrary_atom_with_an_empty_particle_group() {
		createAtomic(
			PutUniqueIdAction.create(RRI.of(this.api.getAddress(), "test1")),
			new CreateEmptyGroupAction()
		);
	}

	private void createTwoTokens(CreateTokenAction.TokenSupplyType tokenSupplyType) {
		createAtomic(buildCreateTokenAction(this.properties1, tokenSupplyType),
			buildCreateTokenAction(this.properties2, tokenSupplyType));
	}

	private void createAtomic(Action... actions) {
		TestObserver<Object> observer = new TestObserver<>();

		Transaction transaction = this.api.createTransaction();
		for (Action action : actions) {
			transaction.stage(action);
		}
		transaction.commitAndPush()
			.toObservable()
			.doOnNext(System.out::println)
			.subscribe(observer);

		this.observers.add(observer);
	}

	private CreateTokenAction buildCreateTokenAction(SpecificProperties properties, CreateTokenAction.TokenSupplyType tokenSupplyType) {
		return CreateTokenAction.create(
			RRI.of(this.api.getAddress(), properties.get(SYMBOL)),
			properties.get(NAME),
			properties.get(DESCRIPTION),
			BigDecimal.valueOf(Long.valueOf(properties.get(TOTAL_SUPPLY))),
			BigDecimal.valueOf(Long.valueOf(properties.get(GRANULARITY))),
			tokenSupplyType);
	}

	private TransferTokensAction buildTransferTokenAction(String symbol, long amount) {
		return TransferTokensAction.create(
			RRI.of(api.getAddress(), symbol),
			api.getAddress(),
			api.getAddress(ECKeyPair.generateNew().getPublicKey()),
			BigDecimal.valueOf(amount)
		);
	}

	@When("^I submit a fixed-supply token-creation request with symbol \"([^\"]*)\" and totalSupply (\\d+) scaled$")
	public void i_submit_a_fixed_supply_token_creation_request_with_symbol_totalSupply(String symbol, int totalSupply) {
		this.properties1.put(SYMBOL, symbol);
		this.properties1.put(TOTAL_SUPPLY, Long.toString(totalSupply));
		createToken(CreateTokenAction.TokenSupplyType.FIXED);
	}

	@When("^I submit a fixed-supply token-creation request with granularity (\\d+) scaled$")
	public void i_submit_a_fixed_supply_token_creation_request_with_granularity(int granularity) {
		this.properties1.put(GRANULARITY, Long.toString(granularity));
		createToken(CreateTokenAction.TokenSupplyType.FIXED);
	}

	@When("^I submit a fixed-supply token-creation request with granularity 0$")
	public void i_submit_a_fixed_supply_token_creation_request_with_granularity_0() {
		this.properties1.put(GRANULARITY, "0");
		createToken(CreateTokenAction.TokenSupplyType.FIXED);
	}

	@When("^I submit a fixed-supply token-creation request with symbol \"([^\"]*)\"$")
	public void i_submit_a_fixed_supply_token_creation_request_with_symbol(String symbol) {
		this.properties1.put(SYMBOL, symbol);
		createToken(CreateTokenAction.TokenSupplyType.FIXED);
	}

	@When("^I submit a token transfer request of (\\d+) scaled for \"([^\"]*)\" to an arbitrary account$")
	public void i_submit_a_token_transfer_request_of_for_to_an_arbitrary_account(int count, String symbol) {
		RRI tokenClass = RRI.of(api.getAddress(), symbol);
		RadixAddress arbitrary = api.getAddress(RadixIdentities.createNew().getPublicKey());
		// Ensure balance is up-to-date.
		api.observeBalance(api.getAddress(), tokenClass)
			.firstOrError()
			.blockingGet();

		TestObserver<Object> observer = new TestObserver<>();
		api.sendTokens(tokenClass, api.getAddress(), arbitrary, BigDecimal.valueOf(count))
			.toObservable()
			.doOnNext(System.out::println)
			.subscribe(observer);
		this.observers.add(observer);
	}

	@When("^I observe the atom being accepted$")
	public void i_observe_the_atom_being_accepted() {
		// "the atom" = most recent atom
		i_can_observe_atom_being_accepted(observers.size());
	}

	@Then("^I can observe the atom being accepted$")
	public void i_can_observe_the_atom_being_accepted() {
		// "the atom" = most recent atom
		i_can_observe_atom_being_accepted(observers.size());
	}

	@Then("^I can observe atom (\\d+) being accepted$")
	public void i_can_observe_atom_being_accepted(int atomNumber) {
		awaitAtomStatus(atomNumber, AtomStatus.STORED);
	}

	@Then("^I can observe the atom being rejected with a validation error$")
	public void i_can_observe_the_atom_being_rejected_as_a_validation_error() {
		// "the atom" = most recent atom
		i_can_observe_atom_being_rejected_as_a_validation_error(observers.size());
	}

	@Then("^I can observe atom (\\d+) being rejected with a validation error$")
	public void i_can_observe_atom_being_rejected_as_a_validation_error(int atomNumber) {
		awaitAtomStatus(atomNumber, AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
	}

	@Then("^I can observe atom (\\d+) being rejected with a failure$")
	public void i_can_observe_atom_being_rejected_with_a_failure(int atomNumber) {
		awaitAtomStatus(atomNumber, AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
	}

	@Then("^I can observe the atom being rejected with an error$")
	public void i_can_observe_atom_being_rejected_with_an_error() {
		// "the atom" = most recent atom
		i_can_observe_atom_being_rejected_with_an_error(observers.size());
	}

	@Then("^I can observe atom (\\d+) being rejected with an error$")
	public void i_can_observe_atom_being_rejected_with_an_error(int atomNumber) {
		awaitAtomStatus(atomNumber, AtomStatus.EVICTED_CONFLICT_LOSER, AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
	}

	@Then("^I can observe token \"([^\"]*)\" balance equal to (\\d+) scaled$")
	public void i_can_observe_token_balance_equal_to_scaled(String symbol, int balance) {
		RRI tokenClass = RRI.of(api.getAddress(), symbol);
		// Ensure balance is up-to-date.
		BigDecimal tokenBalanceDecimal = api.observeBalance(api.getAddress(), tokenClass)
			.firstOrError()
			.blockingGet();
		UInt256 tokenBalance = TokenUnitConversions.unitsToSubunits(tokenBalanceDecimal);
		UInt256 requiredBalance = TokenUnitConversions.unitsToSubunits(balance);
		assertEquals(requiredBalance, tokenBalance);
	}

	private void createToken(CreateTokenAction.TokenSupplyType tokenCreateSupplyType) {
		TestObserver<Object> observer = new TestObserver<>();
		api.createToken(
				RRI.of(api.getAddress(), this.properties1.get(SYMBOL)),
				this.properties1.get(NAME),
				this.properties1.get(DESCRIPTION),
				BigDecimal.valueOf(Long.valueOf(this.properties1.get(TOTAL_SUPPLY))),
				BigDecimal.valueOf(Long.valueOf(this.properties1.get(GRANULARITY))),
				tokenCreateSupplyType)
			.toObservable()
			.doOnNext(System.out::println)
			.subscribe(observer);
		this.observers.add(observer);
	}

	private void awaitAtomStatus(int atomNumber, AtomStatus... finalStates) {
		ImmutableSet<AtomStatus> finalStatesSet = ImmutableSet.<AtomStatus>builder()
			.addAll(Arrays.asList(finalStates))
			.build();

		TestObserver<Object> testObserver = this.observers.get(atomNumber - 1);
		testObserver.awaitTerminalEvent();
		testObserver.assertNoErrors();
		testObserver.assertNoTimeout();
		List<Object> events = testObserver.values();
		assertThat(events).extracting(o -> o.getClass().toString())
			.startsWith(
				SubmitAtomRequestAction.class.toString(),
				SubmitAtomSendAction.class.toString()
			);
		assertThat(events).last()
			.isInstanceOf(SubmitAtomStatusAction.class)
			.extracting(o -> SubmitAtomStatusAction.class.cast(o).getStatusNotification().getAtomStatus())
			.isIn(finalStatesSet);
	}
}
