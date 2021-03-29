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
import com.radixdlt.atom.Substate;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.atommodel.validators.UnregisteredValidatorParticle;
import com.radixdlt.identifiers.RadixAddress;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The registration state of a certain validator address.
 */
final class ValidatorRegistrationState {
	private final Substate substate;
	private final RadixAddress address;
	private final boolean registered;

	private ValidatorRegistrationState(Substate substate, RadixAddress address, boolean registered) {
		this.substate = substate;
		this.address = address;
		this.registered = registered;
	}

	/**
	 * Creates a {@link RegisteredValidatorParticle} to register this validator, if possible.
	 * Note that by default
	 *
	 * @throws IllegalStateException if validator is already registered
	 * @return the well-formed {@link RegisteredValidatorParticle} to register this validator
	 */
	RegisteredValidatorParticle register() {
		return register(null, ImmutableSet.of());
	}

	/**
	 * Creates a {@link RegisteredValidatorParticle} to register this validator, if possible.
	 * @throws IllegalStateException if validator is already registered
	 * @return the well-formed {@link RegisteredValidatorParticle} to register this validator
	 * @param url the optional URL for extra information about this validator
	 * @param allowedDelegators the allowed delegators, or empty if everyone is allowed
	 */
	RegisteredValidatorParticle register(String url, Set<RadixAddress> allowedDelegators) {
		if (this.registered) {
			throw new IllegalStateException(String.format(
				"cannot register validator %s, already registered", address)
			);
		}

		return new RegisteredValidatorParticle(this.address, ImmutableSet.copyOf(allowedDelegators), url);
	}

	/**
	 * Creates an {@link UnregisteredValidatorParticle} to unregister this validator, if possible.
	 * @throws IllegalStateException if validator is not registered
	 * @return the well-formed {@link UnregisteredValidatorParticle} to register this validator
	 */
	UnregisteredValidatorParticle unregister() {
		if (!this.registered) {
			throw new IllegalStateException(String.format(
				"cannot unregister validator %s, not registered",
				address
			));
		}

		return new UnregisteredValidatorParticle(this.address);
	}

	/**
	 * Extracts the latest validator registration of a given address state from a store of particles.
	 * @param store the store of {@link RegisteredValidatorParticle}s and {@link UnregisteredValidatorParticle}s
	 * @param address the address of the validator
	 * @throws IllegalArgumentException if any particle is not of the correct type
	 * @return the latest {@link ValidatorRegistrationState} (may be initial if none)
	 */
	static ValidatorRegistrationState from(Stream<Substate> store, RadixAddress address) {
		Objects.requireNonNull(store, "store");
		Objects.requireNonNull(address, "address");
		return store
			.map(ValidatorRegistrationState::from)
			.filter(state -> state.getAddress().equals(address))
			.findFirst()
			.orElseGet(() -> ValidatorRegistrationState.initial(address));
	}

	/**
	 * Extracts the validator registration state from a given validator registration particle
	 * @throws IllegalArgumentException if the particle is not of the correct type
	 * @return the extracted {@link ValidatorRegistrationState}
	 */
	static ValidatorRegistrationState from(Substate substate) {
		var particle = substate.getParticle();
		if (particle instanceof RegisteredValidatorParticle) {
			RegisteredValidatorParticle validator = (RegisteredValidatorParticle) particle;
			return new ValidatorRegistrationState(substate, validator.getAddress(), true);
		} else if (particle instanceof  UnregisteredValidatorParticle) {
			UnregisteredValidatorParticle unregistered = (UnregisteredValidatorParticle) particle;
			return new ValidatorRegistrationState(substate, unregistered.getAddress(), false);
		} else {
			throw new IllegalArgumentException(String.format("unknown particle: %s", particle));
		}
	}

	/**
	 * Gets the initial validator registration state for a certain address.
	 * The initial state is for a validator to be unregistered at nonce 0.
	 * @param address the validator address
	 * @return the initial {@link ValidatorRegistrationState} for the address
	 */
	static ValidatorRegistrationState initial(RadixAddress address) {
		Objects.requireNonNull(address, "address");
		var initialState = new UnregisteredValidatorParticle(address);
		var substate = new Substate(initialState, SubstateId.ofVirtualSubstate(initialState));
		return ValidatorRegistrationState.from(substate);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ValidatorRegistrationState)) {
			return false;
		}
		ValidatorRegistrationState that = (ValidatorRegistrationState) o;
		return registered == that.registered
			&& Objects.equals(substate, that.substate)
			&& Objects.equals(address, that.address);
	}

	@Override
	public int hashCode() {
		return Objects.hash(substate, address, registered);
	}

	/**
	 * Gets the underlying particle representing this state.
	 */
	Substate asSubstate() {
		return this.substate;
	}

	/**
	 * Gets the address of this validator registration state.
	 */
	RadixAddress getAddress() {
		return address;
	}

	/**
	 * Tests whether this validator is registered at nonce.
	 */
	boolean isRegistered() {
		return registered;
	}
}
