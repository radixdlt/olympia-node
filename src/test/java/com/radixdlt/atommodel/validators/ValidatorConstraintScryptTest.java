/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.atommodel.validators;

import com.radixdlt.atommodel.validators.ValidatorConstraintScrypt.ValidatorTransitionProcedure;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.constraintmachine.WitnessData;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.CMStores;
import org.junit.Before;
import org.junit.Test;

import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercise the critical invariants of the {@link ValidatorConstraintScrypt}.
 *
 * The invariants tested here follow the flow of the validator FSM:
 * <ul>
 *     <li>unregistered validator with nonce 0 is virtualised as UP</li>
 *     <li>validator address must be specified</li>
 *     <li>validator transitions must be between validators of the same address</li>
 *     <li>the nonce must increase by exactly one with every transition</li>
 *     <li>validator transitions must be signed by the address</li>
 * </ul>
 *
 * These tests are implemented by either testing the static check directly or the generated transition procedure.
 */
public class ValidatorConstraintScryptTest {
	private Function<Particle, Result> staticCheck;
	private UnaryOperator<CMStore> virtualLayer;

	@Before
	public void initializeConstraintScrypt() {
		ValidatorConstraintScrypt tokensConstraintScrypt = new ValidatorConstraintScrypt();
		CMAtomOS cmAtomOS = new CMAtomOS();
		cmAtomOS.load(tokensConstraintScrypt);
		staticCheck = cmAtomOS.buildParticleStaticCheck();
		virtualLayer = cmAtomOS.buildVirtualLayer();
	}

	@Test
	public void when_creating_unregistered_validator_with_zero_nonce__spin_is_virtualised_as_up() {
		CMStore virtualisedStore = virtualLayer.apply(CMStores.empty());
		assertThat(virtualisedStore.getSpin(createUnregistered(mock(RadixAddress.class), 0))).isEqualTo(Spin.UP);
	}

	@Test
	public void when_creating_unregistered_validator_with_nonzero_nonce__spin_is_not_virtualised() {
		CMStore virtualisedStore = virtualLayer.apply(CMStores.empty());
		assertThat(virtualisedStore.getSpin(createUnregistered(mock(RadixAddress.class), 1))).isEqualTo(Spin.NEUTRAL);
	}

	@Test
	public void when_validating_unregistered_validator_particle_with_no_address__result_has_error() {
		UnregisteredValidatorParticle validator = mock(UnregisteredValidatorParticle.class);
		assertThat(staticCheck.apply(validator).getErrorMessage()).contains("address");
	}

	@Test
	public void when_validating_registered_validator_particle_with_no_address__result_has_error() {
		RegisteredValidatorParticle validator = mock(RegisteredValidatorParticle.class);
		assertThat(staticCheck.apply(validator).getErrorMessage()).contains("address");
	}

	@Test
	public void when_validating_validator_registration_with_mismatching_addresses__result_has_error() {
		ValidatorTransitionProcedure<UnregisteredValidatorParticle, RegisteredValidatorParticle> procedure = createUnregisteredToRegistered();
		UnregisteredValidatorParticle input = createUnregistered(mock(RadixAddress.class), 0);
		RegisteredValidatorParticle output = createRegistered(mock(RadixAddress.class), 1);
		assertThat(procedure.precondition(input, null, output, null).getErrorMessage())
			.contains("validator addresses");
	}

	@Test
	public void when_validating_validator_unregistration_with_mismatching_addresses__result_has_error() {
		ValidatorTransitionProcedure<RegisteredValidatorParticle, UnregisteredValidatorParticle> procedure = createRegisteredToUnregistered();
		RegisteredValidatorParticle input = createRegistered(mock(RadixAddress.class), 0);
		UnregisteredValidatorParticle output = createUnregistered(mock(RadixAddress.class), 1);
		assertThat(procedure.precondition(input, null, output, null).getErrorMessage())
			.contains("validator addresses");
	}

	@Test
	public void when_validating_validator_registration_with_equal_nonce__result_has_error() {
		assertThat(testRegistrationWithNonces(0, 0).getErrorMessage()).contains("nonce");
	}

	@Test
	public void when_validating_validator_unregistration_with_equal_nonce__result_has_error() {
		assertThat(testUnregistrationWithNonces(0, 0).getErrorMessage()).contains("nonce");
	}

	@Test
	public void when_validating_validator_registration_with_skipped_nonce__result_has_error() {
		assertThat(testRegistrationWithNonces(0, 2).getErrorMessage()).contains("nonce");
	}

	@Test
	public void when_validating_validator_unregistration_with_skipped_nonce__result_has_error() {
		assertThat(testUnregistrationWithNonces(0, 2).getErrorMessage()).contains("nonce");
	}

	@Test
	public void when_validating_validator_registration_with_decreasing_nonce__result_has_error() {
		assertThat(testRegistrationWithNonces(2, 0).getErrorMessage()).contains("nonce");
	}

	@Test
	public void when_validating_validator_unregistration_with_decreasing_nonce__result_has_error() {
		assertThat(testUnregistrationWithNonces(2, 0).getErrorMessage()).contains("nonce");
	}

	@Test
	public void when_validating_validator_registration_with_valid_nonce__result_is_success() {
		assertThat(testRegistrationWithNonces(0, 1).isSuccess()).isTrue();
	}

	@Test
	public void when_validating_validator_unregistration_with_valid_nonce__result_is_success() {
		assertThat(testUnregistrationWithNonces(0, 1).isSuccess()).isTrue();
	}

	@Test
	public void when_validating_validator_registration_without_signature__result_has_error() {
		ValidatorTransitionProcedure<UnregisteredValidatorParticle, RegisteredValidatorParticle> procedure = createUnregisteredToRegistered();
		UnregisteredValidatorParticle input = createUnregistered(mock(RadixAddress.class), 0);
		WitnessData witnessData = mock(WitnessData.class);
		when(witnessData.isSignedBy(any())).thenReturn(false);
		assertThat(procedure.inputWitnessValidator().validate(input, witnessData).getErrorMessage()).contains("not signed");
	}

	@Test
	public void when_validating_validator_unregistration_without_signature__result_has_error() {
		ValidatorTransitionProcedure<RegisteredValidatorParticle, UnregisteredValidatorParticle> procedure = createRegisteredToUnregistered();
		RegisteredValidatorParticle input = createRegistered(mock(RadixAddress.class), 0);
		WitnessData witnessData = mock(WitnessData.class);
		when(witnessData.isSignedBy(any())).thenReturn(false);
		assertThat(procedure.inputWitnessValidator().validate(input, witnessData).getErrorMessage()).contains("not signed");
	}

	@Test
	public void when_validating_validator_registration_with_signature__result_is_success() {
		ValidatorTransitionProcedure<UnregisteredValidatorParticle, RegisteredValidatorParticle> procedure = createUnregisteredToRegistered();
		RadixAddress address = mock(RadixAddress.class);
		UnregisteredValidatorParticle input = createUnregistered(address, 0);
		WitnessData witnessData = mock(WitnessData.class);
		when(witnessData.isSignedBy(eq(address.getPublicKey()))).thenReturn(true);
		assertThat(procedure.inputWitnessValidator().validate(input, witnessData).isSuccess()).isTrue();
	}

	@Test
	public void when_validating_validator_unregistration_with_signature__result_is_success() {
		ValidatorTransitionProcedure<RegisteredValidatorParticle, UnregisteredValidatorParticle> procedure = createRegisteredToUnregistered();
		RadixAddress address = mock(RadixAddress.class);
		RegisteredValidatorParticle input = createRegistered(address, 0);
		WitnessData witnessData = mock(WitnessData.class);
		when(witnessData.isSignedBy(eq(address.getPublicKey()))).thenReturn(true);
		assertThat(procedure.inputWitnessValidator().validate(input, witnessData).isSuccess()).isTrue();
	}

	private Result testRegistrationWithNonces(int inputNonce, int outputNonce) {
		ValidatorTransitionProcedure<UnregisteredValidatorParticle, RegisteredValidatorParticle> procedure = createUnregisteredToRegistered();
		RadixAddress address = mock(RadixAddress.class);
		UnregisteredValidatorParticle input = createUnregistered(address, inputNonce);
		RegisteredValidatorParticle output = createRegistered(address, outputNonce);
		return procedure.precondition(input, null, output, null);
	}

	private Result testUnregistrationWithNonces(int inputNonce, int outputNonce) {
		ValidatorTransitionProcedure<RegisteredValidatorParticle, UnregisteredValidatorParticle> procedure = createRegisteredToUnregistered();
		RadixAddress address = mock(RadixAddress.class);
		RegisteredValidatorParticle input = createRegistered(address, inputNonce);
		UnregisteredValidatorParticle output = createUnregistered(address, outputNonce);
		return procedure.precondition(input, null, output, null);
	}

	private ValidatorTransitionProcedure<UnregisteredValidatorParticle, RegisteredValidatorParticle> createUnregisteredToRegistered() {
		return new ValidatorTransitionProcedure<>(
			UnregisteredValidatorParticle::getAddress,
			UnregisteredValidatorParticle::getNonce,
			RegisteredValidatorParticle::getAddress,
			RegisteredValidatorParticle::getNonce
		);
	}

	private ValidatorTransitionProcedure<RegisteredValidatorParticle, UnregisteredValidatorParticle> createRegisteredToUnregistered() {
		return new ValidatorTransitionProcedure<>(
			RegisteredValidatorParticle::getAddress,
			RegisteredValidatorParticle::getNonce,
			UnregisteredValidatorParticle::getAddress,
			UnregisteredValidatorParticle::getNonce
		);
	}

	private UnregisteredValidatorParticle createUnregistered(RadixAddress address, long nonce) {
		UnregisteredValidatorParticle unregistered = mock(UnregisteredValidatorParticle.class);
		when(unregistered.getAddress()).thenReturn(address);
		when(unregistered.getNonce()).thenReturn(nonce);
		return unregistered;
	}

	private RegisteredValidatorParticle createRegistered(RadixAddress address, long nonce) {
		RegisteredValidatorParticle registered = mock(RegisteredValidatorParticle.class);
		when(registered.getAddress()).thenReturn(address);
		when(registered.getNonce()).thenReturn(nonce);
		return registered;
	}
}
