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
 */

package com.radixdlt.middleware2;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableList;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.atommodel.message.MessageParticle;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware.SpunParticle;
import com.radixdlt.middleware2.LedgerAtom.LedgerAtomConversionException;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class LedgerAtomTest {
	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(LedgerAtom.class)
			.verify();
	}

	private static Atom createApiAtom() {
		RadixAddress address = RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
		Atom atom = new Atom();
		// add a particle to ensure atom is valid and has at least one shard
		atom.addParticleGroupWith(new MessageParticle(address, address, "Hello".getBytes()), Spin.UP);
		return atom;
	}

	@Test
	public void testConvertToApiAtom() throws Exception {
		Atom atom = createApiAtom();
		final LedgerAtom ledgerAtom = LedgerAtom.convertFromApiAtom(atom);
		Atom fromLedgerAtom = LedgerAtom.convertToApiAtom(ledgerAtom);
		assertThat(atom).isEqualTo(fromLedgerAtom);
	}

	@Test
	public void testGetters() throws Exception {
		Atom atom = createApiAtom();
		final LedgerAtom ledgerAtom = LedgerAtom.convertFromApiAtom(atom);
		assertThat(atom.getAID()).isEqualTo(ledgerAtom.getAID());
		assertThat(atom.getMetaData()).isEqualTo(ledgerAtom.getMetaData());
		assertThat(ledgerAtom.getCMInstruction()).isNotNull();
	}

	@Test
	public void when_validating_an_up_cm_particle__no_issue_is_returned() throws Exception {
		Particle particle0 = mock(Particle.class);
		LedgerAtom.toCMMicroInstructions(
			ImmutableList.of(ParticleGroup.of(
				SpunParticle.up(particle0)
			))
		);
	}

	@Test
	public void when_validating_an_up_to_down_cm_particle__no_issue_is_returned() throws Exception {
		Particle particle0 = mock(Particle.class);
		LedgerAtom.toCMMicroInstructions(
			ImmutableList.of(
				ParticleGroup.of(
					SpunParticle.up(particle0)
				),
				ParticleGroup.of(
					SpunParticle.down(particle0)
				)
			)
		);
	}

	@Test
	public void when_validating_an_up_to_up_cm_particle__error_is_returned() {
		Particle particle0 = mock(Particle.class);

		assertThatThrownBy(() ->
			LedgerAtom.toCMMicroInstructions(
				ImmutableList.of(
					ParticleGroup.of(
						SpunParticle.up(particle0)
					),
					ParticleGroup.of(
						SpunParticle.up(particle0)
					)
				)
			)
		)
			.isInstanceOf(LedgerAtomConversionException.class)
			.hasFieldOrPropertyWithValue("dataPointer", DataPointer.ofParticle(1, 0));
	}

	@Test
	public void when_validating_a_down_to_down_cm_particle__error_is_returned() {
		Particle particle0 = mock(Particle.class);
		assertThatThrownBy(() ->
			LedgerAtom.toCMMicroInstructions(
				ImmutableList.of(
					ParticleGroup.of(
						SpunParticle.down(particle0)
					),
					ParticleGroup.of(
						SpunParticle.down(particle0)
					)
				)
			)
		).isInstanceOf(LedgerAtomConversionException.class);
	}

	@Test
	public void when_validating_a_down_to_up_cm_particle__error_is_returned() {
		Particle particle0 = mock(Particle.class);
		assertThatThrownBy(() ->
			LedgerAtom.toCMMicroInstructions(
				ImmutableList.of(
					ParticleGroup.of(
						SpunParticle.down(particle0)
					),
					ParticleGroup.of(
						SpunParticle.up(particle0)
					)
				)
			)
		).isInstanceOf(LedgerAtomConversionException.class);
	}

	@Test
	public void when_checking_two_duplicate_particles__error_is_returned() {
		Particle particle0 = mock(Particle.class);
		assertThatThrownBy(() ->
			LedgerAtom.toCMMicroInstructions(
				ImmutableList.of(
					ParticleGroup.of(
						SpunParticle.up(particle0),
						SpunParticle.down(particle0)
					)
				)
			)
		).isInstanceOf(LedgerAtomConversionException.class);
	}
}