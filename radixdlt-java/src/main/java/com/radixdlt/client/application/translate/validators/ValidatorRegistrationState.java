package com.radixdlt.client.application.translate.validators;

import com.radixdlt.client.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.client.atommodel.validators.UnregisteredValidatorParticle;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.identifiers.RadixAddress;

import java.util.Comparator;
import java.util.stream.Stream;

final class ValidatorRegistrationState {
	private final transient Particle particle;
	private final RadixAddress address;
	private final boolean registered;
	private final long nonce;

	private ValidatorRegistrationState(Particle particle, RadixAddress address, boolean registered, long nonce) {
		this.particle = particle;
		this.address = address;
		this.registered = registered;
		this.nonce = nonce;
	}

	RegisteredValidatorParticle register() {
		if (this.registered) {
			throw new IllegalStateException(String.format(
				"cannot register validator %s, already registered as of %s",
				address, nonce)
			);
		}

		return new RegisteredValidatorParticle(this.address, this.nonce + 1);
	}

	UnregisteredValidatorParticle unregister() {
		if (!this.registered) {
			throw new IllegalStateException(String.format(
				"cannot unregister validator %s, not registered as of %s",
				address, nonce
			));
		}

		return new UnregisteredValidatorParticle(this.address, this.nonce + 1);
	}

	static ValidatorRegistrationState from(Stream<Particle> store, RadixAddress address) {
		return store
			.map(ValidatorRegistrationState::from)
			.filter(state -> state.getAddress().equals(address))
			.max(Comparator.comparing(ValidatorRegistrationState::getNonce))
			.orElseGet(() -> ValidatorRegistrationState.initial(address));
	}

	static ValidatorRegistrationState from(Particle particle) {
		if (particle instanceof RegisteredValidatorParticle) {
			RegisteredValidatorParticle validator = (RegisteredValidatorParticle) particle;
			return new ValidatorRegistrationState(particle, validator.getAddress(), true, validator.getNonce());
		} else if (particle instanceof  UnregisteredValidatorParticle) {
			UnregisteredValidatorParticle unregistered = (UnregisteredValidatorParticle) particle;
			return new ValidatorRegistrationState(particle, unregistered.getAddress(), false, unregistered.getNonce());
		} else {
			throw new IllegalArgumentException(String.format("unknown particle: %s", particle));
		}
	}

	static ValidatorRegistrationState initial(RadixAddress address) {
		UnregisteredValidatorParticle initialState = new UnregisteredValidatorParticle(address, 0);
		return ValidatorRegistrationState.from(initialState);
	}

	Particle asParticle() {
		return this.particle;
	}

	RadixAddress getAddress() {
		return address;
	}

	boolean isRegistered() {
		return registered;
	}

	long getNonce() {
		return nonce;
	}
}
