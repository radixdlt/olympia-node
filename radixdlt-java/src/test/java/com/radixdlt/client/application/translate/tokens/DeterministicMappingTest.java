/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Transaction;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import org.assertj.core.api.SoftAssertions;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;


public class DeterministicMappingTest {
	@Test
	@Ignore("Run locally only.")
	public void when_building_multiple_identical_transactions__then_their_output_should_match() {
		int numIterations = 20;
		RadixIdentity identity1 = RadixIdentities.createNew();
		RadixIdentity identity2 = RadixIdentities.createNew();
		RadixApplicationAPI api = RadixApplicationAPI.create(Bootstrap.LOCALHOST_SINGLENODE, identity1);
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
	@Ignore("Run locally only.")
	public void when_building_multiple_atoms_from_the_same_transaction__then_their_output_should_match() {
		int numIterations = 20;
		RadixIdentity identity1 = RadixIdentities.createNew();
		RadixIdentity identity2 = RadixIdentities.createNew();
		RadixApplicationAPI api = RadixApplicationAPI.create(Bootstrap.LOCALHOST_SINGLENODE, identity1);
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
