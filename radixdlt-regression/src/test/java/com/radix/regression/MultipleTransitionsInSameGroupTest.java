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

package com.radix.regression;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.core.atoms.Atoms;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.AtomStatusEvent;
import com.radixdlt.atom.ParticleGroup;
import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.Notification;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.NotificationType;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import io.reactivex.observers.TestObserver;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import com.radixdlt.utils.UInt256;

import java.util.Arrays;
import java.util.List;

public class MultipleTransitionsInSameGroupTest {
	private RadixUniverse universe = RadixUniverse.create(RadixEnv.getBootstrapConfig());
	private RadixIdentity identity;
	private RadixJsonRpcClient jsonRpcClient;

	@Before
	public void setUp() {
		this.identity = RadixIdentities.createNew();
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), this.identity);
		api.discoverNodes();
		RadixNode node = api.getNetworkState()
			.filter(state -> !state.getNodes().isEmpty())
			.map(state -> state.getNodes().iterator().next())
			.blockingFirst();

		WebSocketClient webSocketClient = new WebSocketClient(listener ->
			HttpClients.getSslAllTrustingClient().newWebSocket(node.getWebSocketEndpoint(), listener)
		);
		webSocketClient.connect();
		webSocketClient.getState()
			.filter(WebSocketStatus.CONNECTED::equals)
			.blockingFirst();
		this.jsonRpcClient = new RadixJsonRpcClient(webSocketClient);
	}

	@Test
	public void when_submitting_an_atom_with_one_down_of_same_consumable_within_a_group__then_atom_is_accepted() {
		RadixAddress myAddress = this.universe.getAddressFrom(this.identity.getPublicKey());
		MutableSupplyTokenDefinitionParticle tokenDefinition = createTokenDefinition(myAddress);
		UnallocatedTokensParticle unallocatedTokens = createUnallocatedTokens(tokenDefinition);
		TransferrableTokensParticle mintedTokens = createTransferrableTokens(myAddress, tokenDefinition, unallocatedTokens.getAmount());
		ParticleGroup definitionGroup = ParticleGroup.builder()
			.virtualSpinDown(new RRIParticle(tokenDefinition.getRRI()))
			.spinUp(tokenDefinition)
			.spinUp(unallocatedTokens)
			.build();
		ParticleGroup mintGroup = ParticleGroup.builder()
			.spinDown(SubstateId.of(unallocatedTokens))
			.spinUp(mintedTokens)
			.build();
		TransferrableTokensParticle output = createTransferrableTokens(myAddress, tokenDefinition, mintedTokens.getAmount());

		ParticleGroup duplicateTransitionsGroup = ParticleGroup.builder()
			.spinDown(SubstateId.of(mintedTokens))
			.spinUp(output)
			.build();

		TestObserver<AtomStatusEvent> result = submitAtom(Arrays.asList(
			definitionGroup,
			mintGroup,
			duplicateTransitionsGroup
		));

		result.awaitCount(1);
		result.assertValue(n -> n.getAtomStatus() == AtomStatus.STORED);
		result.dispose();
	}

	@Test
	public void when_submitting_an_atom_with_two_downs_of_same_consumable_within_a_group__then_atom_is_rejected() {
		RadixAddress myAddress = this.universe.getAddressFrom(this.identity.getPublicKey());
		MutableSupplyTokenDefinitionParticle tokenDefinition = createTokenDefinition(myAddress);
		UnallocatedTokensParticle unallocatedTokens = createUnallocatedTokens(tokenDefinition);
		TransferrableTokensParticle mintedTokens = createTransferrableTokens(myAddress, tokenDefinition, unallocatedTokens.getAmount());
		ParticleGroup definitionGroup = ParticleGroup.builder()
			.virtualSpinDown(new RRIParticle(tokenDefinition.getRRI()))
			.spinUp(tokenDefinition)
			.spinUp(unallocatedTokens)
			.build();
		ParticleGroup mintGroup = ParticleGroup.builder()
			.spinDown(SubstateId.of(unallocatedTokens))
			.spinUp(mintedTokens)
			.build();
		TransferrableTokensParticle output = createTransferrableTokens(
			myAddress,
			tokenDefinition,
			mintedTokens.getAmount().multiply(UInt256.TWO)
		);

		ParticleGroup duplicateTransitionsGroup = ParticleGroup.builder()
			.spinDown(SubstateId.of(mintedTokens))
			.spinDown(SubstateId.of(mintedTokens))
			.spinUp(output)
			.build();

		TestObserver<AtomStatusEvent> result = submitAtom(Arrays.asList(
			definitionGroup,
			mintGroup,
			duplicateTransitionsGroup
		));

		result.awaitCount(1);
		result.assertValue(n -> n.getAtomStatus() == AtomStatus.CONFLICT_LOSER);
		result.dispose();
	}

	@Test
	public void when_submitting_an_atom_with_three_downs_of_same_consumable_within_a_group__then_atom_is_rejected() {
		RadixAddress myAddress = this.universe.getAddressFrom(this.identity.getPublicKey());
		MutableSupplyTokenDefinitionParticle tokenDefinition = createTokenDefinition(myAddress);
		UnallocatedTokensParticle unallocatedTokens = createUnallocatedTokens(tokenDefinition);
		TransferrableTokensParticle mintedTokens = createTransferrableTokens(myAddress, tokenDefinition, unallocatedTokens.getAmount());
		ParticleGroup definitionGroup = ParticleGroup.builder()
			.virtualSpinDown(new RRIParticle(tokenDefinition.getRRI()))
			.spinUp(tokenDefinition)
			.spinUp(unallocatedTokens)
			.build();
		ParticleGroup mintGroup = ParticleGroup.builder()
			.spinDown(SubstateId.of(unallocatedTokens))
			.spinUp(mintedTokens)
			.build();
		TransferrableTokensParticle output = createTransferrableTokens(
			myAddress,
			tokenDefinition,
			mintedTokens.getAmount().multiply(UInt256.THREE)
		);

		ParticleGroup duplicateTransitionsGroup = ParticleGroup.builder()
			.spinDown(SubstateId.of(mintedTokens))
			.spinDown(SubstateId.of(mintedTokens))
			.spinDown(SubstateId.of(mintedTokens))
			.spinUp(output)
			.build();

		TestObserver<AtomStatusEvent> result = submitAtom(Arrays.asList(
			definitionGroup,
			mintGroup,
			duplicateTransitionsGroup
		));

		result.awaitCount(1);
		result.assertValue(n -> n.getAtomStatus() == AtomStatus.CONFLICT_LOSER);
		result.dispose();
	}

	private TransferrableTokensParticle createTransferrableTokens(
		RadixAddress myAddress,
		MutableSupplyTokenDefinitionParticle tokenDefinition,
		UInt256 amount
	) {
		return new TransferrableTokensParticle(
			myAddress,
			amount,
			UInt256.ONE,
			tokenDefinition.getRRI(),
			tokenDefinition.getTokenPermissions(),
			System.nanoTime()
		);
	}

	public void when_submitting_an_atom_with_two_ups_of_same_consumable_within_a_group__then_atom_is_rejected() {
		RadixAddress myAddress = this.universe.getAddressFrom(this.identity.getPublicKey());
		MutableSupplyTokenDefinitionParticle tokenDefinition = createTokenDefinition(myAddress);
		UnallocatedTokensParticle unallocatedTokens = createUnallocatedTokens(tokenDefinition);
		TransferrableTokensParticle mintedTokens = createTransferrableTokens(myAddress, tokenDefinition, unallocatedTokens.getAmount());
		ParticleGroup definitionGroup = ParticleGroup.builder()
			.virtualSpinDown(new RRIParticle(tokenDefinition.getRRI()))
			.spinUp(tokenDefinition)
			.spinUp(unallocatedTokens)
			.build();
		ParticleGroup mintGroup = ParticleGroup.builder()
			.spinDown(SubstateId.of(unallocatedTokens))
			.spinUp(mintedTokens)
			.build();
		TransferrableTokensParticle output = createTransferrableTokens(
			myAddress,
			tokenDefinition,
			mintedTokens.getAmount().divide(UInt256.TWO)
		);

		ParticleGroup duplicateTransitionsGroup = ParticleGroup.builder()
			.spinDown(SubstateId.of(mintedTokens))
			.spinUp(output)
			.spinUp(output)
			.build();

		TestObserver<AtomStatusEvent> result = submitAtom(Arrays.asList(
			definitionGroup,
			mintGroup,
			duplicateTransitionsGroup
		));
		result.awaitCount(1);
		result.assertValue(n -> n.getAtomStatus() == AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
		result.dispose();
	}

	private UnallocatedTokensParticle createUnallocatedTokens(MutableSupplyTokenDefinitionParticle tokenDefinition) {
		return new UnallocatedTokensParticle(
			UInt256.MAX_VALUE,
			UInt256.ONE,
			tokenDefinition.getRRI(),
			tokenDefinition.getTokenPermissions(),
			System.nanoTime()
		);
	}

	private MutableSupplyTokenDefinitionParticle createTokenDefinition(RadixAddress myAddress) {
		return new MutableSupplyTokenDefinitionParticle(
			RRI.of(myAddress, "FLO"),
			"Cookie Token",
			"Cookies!",
			UInt256.ONE,
			null,
			null,
			ImmutableMap.of(
				TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY,
				TokenTransition.BURN, TokenPermission.TOKEN_OWNER_ONLY
			)
		);
	}

	private TestObserver<AtomStatusEvent> submitAtom(List<ParticleGroup> particleGroups) {
		// Warning: fake fee using magic
		TxLowLevelBuilder unsignedAtom = Atom.newBuilder();
		for (var pg : particleGroups) {
			for (CMMicroInstruction i : pg.getInstructions()) {
				if (i.getMicroOp() == CMMicroInstruction.CMMicroOp.SPIN_UP) {
					unsignedAtom.up(i.getParticle());
				} else if (i.getMicroOp() == CMMicroInstruction.CMMicroOp.VIRTUAL_SPIN_DOWN) {
					unsignedAtom.virtualDown(i.getParticle());
				} else if (i.getMicroOp() == CMMicroInstruction.CMMicroOp.SPIN_DOWN) {
					unsignedAtom.down(i.getParticleId());
				}
			}
			unsignedAtom.particleGroup();
		}
		unsignedAtom.message("magic:0xdeadbeef");
		// Sign and submit
		var signedAtom = this.identity.addSignature(unsignedAtom).blockingGet();

		TestObserver<AtomStatusEvent> observer = TestObserver.create();

		final String subscriberId = UUID.randomUUID().toString();
		this.jsonRpcClient.observeAtomStatusNotifications(subscriberId)
			.doOnNext(n -> {
				if (n.getType() == NotificationType.START) {
					this.jsonRpcClient.sendGetAtomStatusNotifications(subscriberId, Atoms.atomIdOf(signedAtom)).blockingAwait();
					this.jsonRpcClient.pushAtom(signedAtom).blockingAwait();
				}
			})
			.filter(n -> n.getType().equals(NotificationType.EVENT))
			.map(Notification::getEvent)
			.subscribe(observer);

		return observer;
	}
}
