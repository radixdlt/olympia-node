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
import com.radix.test.utils.TokenUtilities;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.atom.ParticleGroup;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.atom.SpunParticle;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import com.radixdlt.utils.UInt256;

public class UnallocatedTokensParticleTest {
	@Test
	public void given_an_account__when_the_account_executes_a_token_creation_without_unallocated_particles__then_the_atom_will_be_rejected() {
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), RadixIdentities.createNew());
		TokenUtilities.requestTokensFor(api);

		List<ParticleGroup> groups = new ArrayList<>();

		MutableSupplyTokenDefinitionParticle particle = new MutableSupplyTokenDefinitionParticle(
			RRI.of(api.getAddress(), "JOSH"),
			"Joshy Token",
			"Best Token",
			UInt256.ONE,
			null,
			null,
			ImmutableMap.of(
				TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY,
				TokenTransition.BURN, TokenPermission.NONE
			)
		);

		groups.add(ParticleGroup.of(SpunParticle.up(particle)));

		Atom unsignedAtom = api.buildAtomWithFee(groups);

		Observable<SubmitAtomAction> updates = api.getIdentity()
			.addSignature(unsignedAtom)
			.flatMapObservable(a -> api.submitAtom(a).toObservable());

		TestObserver<SubmitAtomStatusAction> testObserver = TestObserver.create();
		updates
			.doOnNext(System.out::println)
			.ofType(SubmitAtomStatusAction.class)
			.subscribe(testObserver);

		testObserver.awaitTerminalEvent();
		testObserver.assertValue(i -> i.getStatusNotification().getAtomStatus() == AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
	}

	@Test
	public void given_an_account_with_a_token__when_the_account_executes_an_atom_with_unallocated_particles_to_that_token__then_error() {
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), RadixIdentities.createNew());
		TokenUtilities.requestTokensFor(api);

		api.createToken(
			RRI.of(api.getAddress(), "JOSH"),
			"Joshy Token",
			"Coolest Token",
			BigDecimal.ONE,
			BigDecimal.ONE,
			TokenSupplyType.FIXED
		).blockUntilComplete();

		List<ParticleGroup> groups = new ArrayList<>();

		UnallocatedTokensParticle unallocatedParticle = new UnallocatedTokensParticle(
			UInt256.MAX_VALUE,
			UInt256.ONE,
			RRI.of(api.getAddress(), "JOSH"),
			ImmutableMap.of(TokenTransition.MINT, TokenPermission.ALL, TokenTransition.BURN, TokenPermission.ALL),
			System.currentTimeMillis()
		);

		groups.add(ParticleGroup.of(SpunParticle.up(unallocatedParticle)));

		Atom unsignedAtom = api.buildAtomWithFee(groups);

		Observable<SubmitAtomAction> updates = api.getIdentity()
			.addSignature(unsignedAtom)
			.flatMapObservable(a -> api.submitAtom(a).toObservable());

		TestObserver<SubmitAtomStatusAction> testObserver = TestObserver.create();
		updates
			.doOnNext(System.out::println)
			.ofType(SubmitAtomStatusAction.class)
			.subscribe(testObserver);

		testObserver.awaitTerminalEvent();
		testObserver.assertValue(i -> i.getStatusNotification().getAtomStatus() == AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
	}

	@Test
	public void given_an_account__when_the_account_executes_a_token_creation_with_2_unallocated_particles__then_the_atom_will_be_rejected() {
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), RadixIdentities.createNew());
		TokenUtilities.requestTokensFor(api);

		List<ParticleGroup> groups = new ArrayList<>();

		UnallocatedTokensParticle unallocatedParticle0 = new UnallocatedTokensParticle(
			UInt256.MAX_VALUE,
			UInt256.ONE,
			RRI.of(api.getAddress(), "JOSH"),
			ImmutableMap.of(TokenTransition.MINT, TokenPermission.ALL, TokenTransition.BURN, TokenPermission.ALL),
			System.nanoTime()
		);

		UnallocatedTokensParticle unallocatedParticle1 = new UnallocatedTokensParticle(
			UInt256.MAX_VALUE,
			UInt256.ONE,
			RRI.of(api.getAddress(), "JOSH"),
			ImmutableMap.of(TokenTransition.MINT, TokenPermission.ALL, TokenTransition.BURN, TokenPermission.ALL),
			System.nanoTime()
		);

		MutableSupplyTokenDefinitionParticle tokenDefinitionParticle = new MutableSupplyTokenDefinitionParticle(
			RRI.of(api.getAddress(), "JOSH"),
			"Joshy Token",
			"Coolest token",
			UInt256.ONE,
			null,
			null,
			ImmutableMap.of(
				TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY,
				TokenTransition.BURN, TokenPermission.NONE
			)
		);

		groups.add(ParticleGroup.of(
			SpunParticle.up(unallocatedParticle0),
			SpunParticle.up(unallocatedParticle1),
			SpunParticle.up(tokenDefinitionParticle)
		));

		Atom unsignedAtom = api.buildAtomWithFee(groups);

		Observable<SubmitAtomAction> updates = api.getIdentity()
			.addSignature(unsignedAtom)
			.flatMapObservable(a -> api.submitAtom(a).toObservable());

		TestObserver<SubmitAtomStatusAction> testObserver = TestObserver.create();
		updates
			.doOnNext(System.out::println)
			.ofType(SubmitAtomStatusAction.class)
			.subscribe(testObserver);

		testObserver.awaitTerminalEvent();
		testObserver.assertValue(i -> i.getStatusNotification().getAtomStatus() == AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
	}
}
