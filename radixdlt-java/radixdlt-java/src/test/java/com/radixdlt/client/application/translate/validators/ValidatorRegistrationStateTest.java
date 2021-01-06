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

package com.radixdlt.client.application.translate.validators;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.client.atommodel.validators.UnregisteredValidatorParticle;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.identifiers.RadixAddress;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

public class ValidatorRegistrationStateTest {
	private static final RadixAddress ADDRESS_2 = RadixAddress.from("23B6fH3FekJeP6e5guhZAk6n9z4fmTo5Tngo3a11Wg5R8gsWTV2x");
	private static final RadixAddress ADDRESS_1 = RadixAddress.from("JEbhKQzBn4qJzWJFBbaPioA2GTeaQhuUjYWkanTE6N8VvvPpvM8");

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(ValidatorRegistrationState.class)
			// Prefab instances of abstract class "Particle" all serialize to the same empty
			// data, and therefore are always equals(...) and have the same hashCode().
			.withIgnoredFields("particle")
			.verify();
	}

	@Test
	public void when_creating_validator_registration_state_with_null__then_exception_is_thrown() {
		Assertions.assertThatThrownBy(() -> ValidatorRegistrationState.from(null))
			.isInstanceOf(NullPointerException.class);
		Assertions.assertThatThrownBy(() -> ValidatorRegistrationState.from(null, mock(RadixAddress.class)))
			.isInstanceOf(NullPointerException.class);
		Assertions.assertThatThrownBy(() -> ValidatorRegistrationState.from(Stream.empty(), null))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	public void when_registering_unregistered_particle__then_next_state_is_returned() {
		UnregisteredValidatorParticle input = new UnregisteredValidatorParticle(
			ADDRESS_1,
			1
		);
		RegisteredValidatorParticle output = ValidatorRegistrationState.from(input).register("url", ImmutableSet.of());
		assertThat(output.getAddress()).isEqualTo(input.getAddress());
		assertThat(output.getUrl()).isEqualTo("url");
		assertThat(output.getNonce()).isEqualTo(2);
	}

	@Test
	public void when_registering_registered_particle__then_exception_is_thrown() {
		RegisteredValidatorParticle input = new RegisteredValidatorParticle(
			ADDRESS_1,
			1
		);
		assertThatThrownBy(() -> ValidatorRegistrationState.from(input).register())
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void when_unregistering_registered_particle__then_next_state_is_returned() {
		RegisteredValidatorParticle input = new RegisteredValidatorParticle(
			ADDRESS_1,
			1
		);
		UnregisteredValidatorParticle output = ValidatorRegistrationState.from(input).unregister();
		assertThat(output.getAddress()).isEqualTo(input.getAddress());
		assertThat(output.getNonce()).isEqualTo(2);
	}

	@Test
	public void when_unregistering_unregistered_particle__then_exception_is_thrown() {
		UnregisteredValidatorParticle input = new UnregisteredValidatorParticle(
			ADDRESS_1,
			1
		);
		assertThatThrownBy(() -> ValidatorRegistrationState.from(input).unregister())
			.isInstanceOf(IllegalStateException.class);
	}

	/**
	 * Generates numStates consecutive validator registration states at the given address
	 * @param address the address
	 * @param numStates the number of state changes
	 * @param particles list where particles generated along the way will be stored (in order of generation)
	 * @return the final state after numStates - 1 state changes, starting with the initial state
	 */
	private static ValidatorRegistrationState populateStateChanges(RadixAddress address, int numStates, List<Particle> particles) {
		UnregisteredValidatorParticle input = new UnregisteredValidatorParticle(address, 0);
		ValidatorRegistrationState currentState = ValidatorRegistrationState.from(input);
		for (int i = 0; i < numStates - 1; i++) {
			particles.add(currentState.asParticle());
			Particle nextState = currentState.isRegistered() ? currentState.unregister() : currentState.register();
			currentState = ValidatorRegistrationState.from(nextState);
		}
		particles.add(currentState.asParticle()); // add last state
		return currentState;
	}

	@Test
	public void when_extracting_from_stream_with_single_address__then_latest_correct_state_is_returned() {
		int numStateChanges = 10;
		List<Particle> particles = new ArrayList<>(numStateChanges);
		ValidatorRegistrationState currentState = populateStateChanges(ADDRESS_1, numStateChanges, particles);
		ValidatorRegistrationState extractedState = ValidatorRegistrationState.from(particles.stream(), ADDRESS_1);
		assertThat(extractedState).isEqualTo(currentState);
	}

	@Test
	public void when_extracting_from_stream_with_multiple_addresses__then_latest_correct_state_is_returned_for_each() {
		List<Particle> particles = new ArrayList<>();
		ValidatorRegistrationState currentState1 = populateStateChanges(ADDRESS_1, 5, particles);
		ValidatorRegistrationState currentState2 = populateStateChanges(ADDRESS_2, 6, particles);

		ValidatorRegistrationState extractedState1 = ValidatorRegistrationState.from(particles.stream(), ADDRESS_1);
		assertThat(extractedState1).isEqualTo(currentState1);
		ValidatorRegistrationState extractedState2 = ValidatorRegistrationState.from(particles.stream(), ADDRESS_2);
		assertThat(extractedState2).isEqualTo(currentState2);
	}


	@Test
	public void when_converting_from_invalid_particle__then_exception_is_thrown() {
		Assertions.assertThatThrownBy(() -> ValidatorRegistrationState.from(mock(Particle.class)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void when_converting_from_registered_particle__then_correct_state_is_returned() {
		RegisteredValidatorParticle particle = new RegisteredValidatorParticle(
			ADDRESS_1,
			1
		);
		ValidatorRegistrationState state = ValidatorRegistrationState.from(particle);
		assertThat(state.asParticle()).isEqualTo(particle);
		assertThat(state.isRegistered()).isTrue();
		assertThat(state.getAddress()).isEqualTo(particle.getAddress());
		assertThat(state.getNonce()).isEqualTo(particle.getNonce());
	}

	@Test
	public void when_converting_from_unregistered_particle__then_correct_state_is_returned() {
		UnregisteredValidatorParticle particle = new UnregisteredValidatorParticle(
			ADDRESS_1,
			1
		);
		ValidatorRegistrationState state = ValidatorRegistrationState.from(particle);
		assertThat(state.asParticle()).isEqualTo(particle);
		assertThat(state.isRegistered()).isFalse();
		assertThat(state.getAddress()).isEqualTo(particle.getAddress());
		assertThat(state.getNonce()).isEqualTo(particle.getNonce());
	}

	@Test
	public void when_creating_initial_state__then_correct_state_is_returned() {
		RadixAddress address = ADDRESS_1;
		ValidatorRegistrationState state = ValidatorRegistrationState.initial(address);
		assertThat(state.asParticle()).isInstanceOf(UnregisteredValidatorParticle.class);
		assertThat(state.isRegistered()).isFalse();
		assertThat(state.getAddress()).isEqualTo(address);
		assertThat(state.getNonce()).isEqualTo(0L);
	}
}
