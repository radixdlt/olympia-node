/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radix.regression;

import com.radix.test.utils.TokenUtilities;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Transaction;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.application.translate.tokens.TransferTokensAction;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import org.assertj.core.api.SoftAssertions;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;


public class DeterministicMappingTest {
	@Test
	public void when_building_multiple_identical_transactions__then_their_output_should_match() {
		int numIterations = 20;
		RadixIdentity identity2 = RadixIdentities.createNew();
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), RadixIdentities.createNew());
		RadixAddress address1 = api.getAddress();
		RadixAddress address2 = api.getAddress(identity2.getPublicKey());
		RRI token = RRI.of(address1, "TestToken");
		Supplier<Transaction> txGenerator = () -> buildTransaction(api, address1, address2, token);

		Transaction goldenTx = txGenerator.get();
		List<SpunParticle> goldenParticles = api.getAtomStore().getStagedAndClear(goldenTx.getUuid())
			.stream()
			.flatMap(ParticleGroup::spunParticles)
			.collect(Collectors.toList());
		for (int i = 0; i < numIterations; i++) {
			Transaction sampleTx = txGenerator.get();
			List<SpunParticle> sampleParticles = api.getAtomStore().getStagedAndClear(sampleTx.getUuid())
				.stream()
				.flatMap(ParticleGroup::spunParticles)
				.collect(Collectors.toList());
			SoftAssertions.assertSoftly(softly -> softly.assertThat(sampleParticles.size()).isEqualTo(goldenParticles.size()));
		}
	}

	@Test
	public void when_building_multiple_atoms_from_the_same_transaction__then_their_output_should_match() {
		int numIterations = 20;
		RadixIdentity identity2 = RadixIdentities.createNew();
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), RadixIdentities.createNew());
		TokenUtilities.requestTokensFor(api);
		RadixAddress address1 = api.getAddress();
		RadixAddress address2 = api.getAddress(identity2.getPublicKey());
		RRI token = RRI.of(address1, "TestToken");
		Supplier<Atom> atomGenerator = () -> buildTransaction(api, address1, address2, token).buildAtom();

		Atom goldenAtom = atomGenerator.get();
		List<SpunParticle> goldenParticles = goldenAtom.spunParticles().collect(Collectors.toList());
		for (int i = 0; i < numIterations; i++) {
			Atom sampleAtom = atomGenerator.get();
			List<SpunParticle> sampleParticles = sampleAtom.spunParticles().collect(Collectors.toList());

			SoftAssertions.assertSoftly(softly -> softly.assertThat(sampleParticles.size()).isEqualTo(goldenParticles.size()));
			for (int j = 0; j < goldenParticles.size(); j++) {
				if (j >= sampleParticles.size()) {
					break;
				}
				SpunParticle goldenParticle = goldenParticles.get(j);
				SpunParticle sampleParticle = sampleParticles.get(j);
				SoftAssertions.assertSoftly(softly -> softly.assertThat(sampleParticle.getParticle().getClass())
					.isEqualTo(goldenParticle.getParticle().getClass()));
				SoftAssertions.assertSoftly(softly -> softly.assertThat(sampleParticle.getSpin())
					.isEqualTo(goldenParticle.getSpin()));
			}
		}
	}

	private Transaction buildTransaction(RadixApplicationAPI api, RadixAddress address1, RadixAddress address2, RRI token) {
		Transaction transaction = api.createTransaction();
		transaction.stage(CreateTokenAction.create(token,
			"TestToken",
			"test description",
			BigDecimal.valueOf(1000L),
			BigDecimal.ONE,
			CreateTokenAction.TokenSupplyType.FIXED)
		);
		transaction.stage(TransferTokensAction.create(token, address1, address2, BigDecimal.valueOf(1L)));
		transaction.stage(TransferTokensAction.create(token, address1, address2, BigDecimal.valueOf(2L)));
		transaction.stage(TransferTokensAction.create(token, address1, address2, BigDecimal.valueOf(3L)));
		return transaction;
	}
}
