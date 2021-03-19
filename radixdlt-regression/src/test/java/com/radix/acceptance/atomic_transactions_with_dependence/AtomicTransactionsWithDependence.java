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

package com.radix.acceptance.atomic_transactions_with_dependence;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.radix.acceptance.SpecificProperties;
import com.radix.test.utils.TokenUtilities;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Transaction;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.tokens.AtomToTokenTransfersMapper;
import com.radixdlt.client.application.translate.tokens.BurnTokensAction;
import com.radixdlt.client.application.translate.tokens.BurnTokensActionMapper;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.application.translate.tokens.CreateTokenToParticleGroupsMapper;
import com.radixdlt.client.application.translate.tokens.MintTokensAction;
import com.radixdlt.client.application.translate.tokens.TokenBalanceReducer;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionsReducer;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.client.application.translate.tokens.TransferTokensAction;
import com.radixdlt.client.application.translate.tokens.TransferTokensToParticleGroupsMapper;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.atom.ParticleGroup;
import com.radixdlt.atom.ParticleGroup.ParticleGroupBuilder;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.reactivex.observers.TestObserver;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * See <a href="https://radixdlt.atlassian.net/browse/RLAU-645">RLAU-645</a>.
 */
public class AtomicTransactionsWithDependence {
	private static final String NAME = "name";
	private static final String SYMBOL = "symbol";
	private static final String DESCRIPTION = "description";
	private static final String INITIAL_SUPPLY = "initialSupply";
	private static final String NEW_SUPPLY = "newSupply";
	private static final String GRANULARITY = "granularity";

	private final SpecificProperties properties = SpecificProperties.of(
		NAME,           "RLAU-40 Test token",
		SYMBOL,			"RLAU",
		DESCRIPTION,	"RLAU-40 Test token",
		INITIAL_SUPPLY,	scaledToUnscaled(1000000000),
		NEW_SUPPLY,		scaledToUnscaled(1000000000),
		GRANULARITY,	"1"
	);
	private final List<TestObserver<Object>> observers = Lists.newArrayList();
	private RadixApplicationAPI api;
	private RadixNode nodeConnection;

	@Given("^I have access to a suitable Radix network$")
	public void i_have_access_to_a_suitable_Radix_network() {
		this.api = RadixApplicationAPI.defaultBuilder()
			.universe(RadixUniverse.create(RadixEnv.getBootstrapConfig()))
			.identity(RadixIdentities.createNew())
			.build();
		TokenUtilities.requestTokensFor(api);
		this.api.discoverNodes();
		this.nodeConnection = this.api.getNetworkState()
			.map(RadixNetworkState::getNodes)
			.filter(s -> !s.isEmpty())
			.map(s -> s.iterator().next())
			.firstOrError()
			.blockingGet();

		// Reset data
		this.properties.clear();
		this.observers.clear();
	}

	private void mintAndTransferTokensWith(MintAndTransferTokensActionMapper actionMapper) {
		RadixApplicationAPI api = new RadixApplicationAPI.RadixApplicationAPIBuilder()
			.defaultFeeProcessor()
			.universe(RadixUniverse.create(RadixEnv.getBootstrapConfig()))
			.addStatelessParticlesMapper(CreateTokenAction.class, new CreateTokenToParticleGroupsMapper())
			.addStatefulParticlesMapper(MintAndTransferTokensAction.class, actionMapper)
			.addStatefulParticlesMapper(TransferTokensAction.class, new TransferTokensToParticleGroupsMapper())
			.addStatefulParticlesMapper(BurnTokensAction.class, new BurnTokensActionMapper()) // Required for fees
			.addReducer(new TokenDefinitionsReducer())
			.addReducer(new TokenBalanceReducer())
			.addAtomMapper(new AtomToTokenTransfersMapper())
			.identity(RadixIdentities.createNew())
			.build();
		TokenUtilities.requestTokensFor(api);

		this.properties.put(SYMBOL, "TEST0");
		createToken(CreateTokenAction.TokenSupplyType.MUTABLE, api);
		i_can_observe_atom_being_accepted(1);
		this.observers.clear();

		RadixIdentity toIdentity = RadixIdentities.createNew();
		RadixAddress toAddress = api.getAddress(toIdentity.getPublicKey());
		TestObserver<Object> observer = new TestObserver<>();
		Transaction tx = api.createTransaction();
		tx.stage(new MintAndTransferTokensAction(RRI.of(api.getAddress(), "TEST0"), BigDecimal.valueOf(7), toAddress));
		tx.commitAndPush(nodeConnection)
			.toObservable()
			.subscribe(observer);
		observers.add(observer);
	}

	@When("^I submit a particle group spending a consumable that was created in a group with a lower index$")
	public void iSubmitAParticleGroupSpendingAConsumableThatWasCreatedInAGroupWithALowerIndex() throws Exception {
		this.properties.put(SYMBOL, "TEST0");
		createToken(CreateTokenAction.TokenSupplyType.MUTABLE, api);
		i_can_observe_atom_being_accepted(1);
		this.observers.clear();

		RadixIdentity toIdentity = RadixIdentities.createNew();
		RadixAddress toAddress = api.getAddress(toIdentity.getPublicKey());
		TestObserver<Object> observer = new TestObserver<>();
		Transaction transaction = api.createTransaction();
		transaction.stage(MintTokensAction.create(RRI.of(api.getAddress(), "TEST0"), api.getAddress(), BigDecimal.valueOf(7)));
		transaction.stage(TransferTokensAction.create(RRI.of(api.getAddress(), "TEST0"), api.getAddress(), toAddress, BigDecimal.valueOf(7)));
		transaction.commitAndPush(nodeConnection)
			.toObservable()
			.doOnNext(System.out::println)
			.subscribe(observer);
		observers.add(observer);
	}

	@When("^I submit a particle group spending a consumable that was created in same group$")
	public void iSubmitAParticleGroupSpendingAConsumableThatWasCreatedInSameGroup() {
		mintAndTransferTokensWith(new MintAndTransferTokensActionMapper((mintTransition, transferTransition) -> {

			ParticleGroupBuilder groupBuilder = ParticleGroup.builder();
			mintTransition.getRemoved().stream().map(t -> (Particle) t).forEach(p -> groupBuilder.addParticle(p, Spin.DOWN));
			mintTransition.getMigrated().stream().map(t -> (Particle) t).forEach(p -> groupBuilder.addParticle(p, Spin.UP));
			mintTransition.getTransitioned().stream().map(t -> (Particle) t).forEach(p -> groupBuilder.addParticle(p, Spin.UP));
			transferTransition.getRemoved().stream().map(t -> (Particle) t).forEach(p -> groupBuilder.addParticle(p, Spin.DOWN));
			transferTransition.getMigrated().stream().map(t -> (Particle) t).forEach(p -> groupBuilder.addParticle(p, Spin.UP));
			transferTransition.getTransitioned().stream().map(t -> (Particle) t).forEach(p -> groupBuilder.addParticle(p, Spin.UP));

			return Collections.singletonList(groupBuilder.build());
		}));
	}

	@When("^I submit a particle group spending a consumable that was created in a group with a higher index$")
	public void iSubmitAParticleGroupSpendingAConsumableThatWasCreatedInAGroupWithAHigherIndex() {
		mintAndTransferTokensWith(new MintAndTransferTokensActionMapper((mint, transfer) -> {
			ParticleGroupBuilder mintParticleGroupBuilder = ParticleGroup.builder();
			mint.getRemoved().stream().map(t -> (Particle) t).forEach(p -> mintParticleGroupBuilder.addParticle(p, Spin.DOWN));
			mint.getMigrated().stream().map(t -> (Particle) t).forEach(p -> mintParticleGroupBuilder.addParticle(p, Spin.UP));
			mint.getTransitioned().stream().map(t -> (Particle) t).forEach(p -> mintParticleGroupBuilder.addParticle(p, Spin.UP));

			ParticleGroupBuilder transferParticleGroupBuilder = ParticleGroup.builder();
			transfer.getRemoved().stream().map(t -> (Particle) t).forEach(p -> transferParticleGroupBuilder.addParticle(p, Spin.DOWN));
			transfer.getMigrated().stream().map(t -> (Particle) t).forEach(p -> transferParticleGroupBuilder.addParticle(p, Spin.UP));
			transfer.getTransitioned().stream().map(t -> (Particle) t).forEach(p -> transferParticleGroupBuilder.addParticle(p, Spin.UP));

			return Arrays.asList(
				transferParticleGroupBuilder.build(),
				mintParticleGroupBuilder.build()
			);
		}));
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
		awaitAtomStatus(atomNumber, AtomStatus.CONFLICT_LOSER);
	}

	private void createToken(CreateTokenAction.TokenSupplyType tokenCreateSupplyType, RadixApplicationAPI api) {
		TestObserver<Object> observer = new TestObserver<>();
		Transaction tx = api.createTransaction();
		CreateTokenAction createTokenAction = CreateTokenAction.create(
			RRI.of(api.getAddress(), this.properties.get(SYMBOL)),
			this.properties.get(NAME),
			this.properties.get(DESCRIPTION),
			new BigDecimal(this.properties.get(INITIAL_SUPPLY)),
			new BigDecimal(this.properties.get(GRANULARITY)),
			tokenCreateSupplyType
		);
		tx.stage(createTokenAction);
		tx.commitAndPush(nodeConnection)
			.toObservable()
			.doOnNext(System.out::println)
			.subscribe(observer);
		observers.add(observer);
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
			.startsWith(SubmitAtomSendAction.class.toString());
		assertThat(events).last()
			.isInstanceOf(SubmitAtomStatusAction.class)
			.extracting(o -> SubmitAtomStatusAction.class.cast(o).getStatusNotification().getAtomStatus())
			.isIn(finalStatesSet);
	}

	private static String scaledToUnscaled(int amount) {
		return TokenUnitConversions.unitsToSubunits(amount).toString();
	}
}
