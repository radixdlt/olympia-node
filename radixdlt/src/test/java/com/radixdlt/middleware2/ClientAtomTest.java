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

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableList;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware.SpunParticle;
import com.radixdlt.middleware2.ClientAtom.LedgerAtomConversionException;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class ClientAtomTest {
	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(ClientAtom.class)
			.verify();
	}

	@Test
	public void when_validating_an_up_cm_particle__no_issue_is_returned() throws Exception {
		Particle particle0 = mock(Particle.class);
		ClientAtom.toCMMicroInstructions(
			ImmutableList.of(ParticleGroup.of(
				SpunParticle.up(particle0)
			))
		);
	}

	@Test
	public void when_validating_an_up_to_down_cm_particle__no_issue_is_returned() throws Exception {
		Particle particle0 = mock(Particle.class);
		ClientAtom.toCMMicroInstructions(
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
	public void when_validating_an_up_to_up_cm_particle__internal_conflict_is_returned() {
		Particle particle0 = mock(Particle.class);

		assertThatThrownBy(() ->
			ClientAtom.toCMMicroInstructions(
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
	public void when_validating_a_down_to_down_cm_particle__conflict_is_returned() {
		Particle particle0 = mock(Particle.class);
		assertThatThrownBy(() ->
			ClientAtom.toCMMicroInstructions(
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
	public void when_validating_a_down_to_up_cm_particle__single_conflict_is_returned() {
		Particle particle0 = mock(Particle.class);
		assertThatThrownBy(() ->
			ClientAtom.toCMMicroInstructions(
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
	public void when_checking_two_duplicate_particles__two_errors_are_returned() {
		Particle particle0 = mock(Particle.class);
		assertThatThrownBy(() ->
			ClientAtom.toCMMicroInstructions(
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