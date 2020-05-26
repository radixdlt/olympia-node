package com.radixdlt.client.atommodel.validators;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.application.translate.ShardedParticleStateId;
import com.radixdlt.client.application.translate.StageActionException;
import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.client.application.translate.validators.RegisterValidatorAction;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.identifiers.RadixAddress;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class RegisterValidatorActionMapper implements StatefulActionToParticleGroupsMapper<RegisterValidatorAction> {
	@Override
	public Set<ShardedParticleStateId> requiredState(RegisterValidatorAction action) {
		return ImmutableSet.of(
			ShardedParticleStateId.of(RegisteredValidatorParticle.class, action.getValidator()),
			ShardedParticleStateId.of(UnregisteredValidatorParticle.class, action.getValidator())
		);
	}

	@Override
	public List<ParticleGroup> mapToParticleGroups(RegisterValidatorAction action, Stream<Particle> store) throws StageActionException {
		ValidatorRegistrationState currentState = store
			.map(ValidatorRegistrationState::from)
			.filter(state -> state.address.equals(action.getValidator()))
			.max(Comparator.comparing(ValidatorRegistrationState::getNonce))
			.orElseGet(() -> ValidatorRegistrationState.initial(action.getValidator()));

		return ImmutableList.of(
			ParticleGroup.of(SpunParticle.up(currentState.register()))
		);
	}

	private static final class ValidatorRegistrationState {
		private final RadixAddress address;
		private final boolean registered;
		private final long nonce;

		private ValidatorRegistrationState(RadixAddress address, boolean registered, long nonce) {
			this.address = address;
			this.registered = registered;
			this.nonce = nonce;
		}

		private RegisteredValidatorParticle register() {
			if (this.registered) {
				throw new IllegalStateException(String.format(
					"cannot register validator %s, already registered as of %s",
					address, nonce)
				);
			}

			return new RegisteredValidatorParticle(this.address, this.nonce + 1);
		}

		private UnregisteredValidatorParticle unregister() {
			if (!this.registered) {
				throw new IllegalStateException(String.format(
					"cannot unregister validator %s, not registered as of %s",
					address, nonce
				));
			}

			return new UnregisteredValidatorParticle(this.address, this.nonce + 1);
		}

		private static ValidatorRegistrationState from(Particle particle) {
			if (particle instanceof RegisteredValidatorParticle) {
				RegisteredValidatorParticle validator = (RegisteredValidatorParticle) particle;
				return new ValidatorRegistrationState(validator.getAddress(), true, validator.getNonce());
			} else if (particle instanceof  UnregisteredValidatorParticle) {
				UnregisteredValidatorParticle unregistered = (UnregisteredValidatorParticle) particle;
				return new ValidatorRegistrationState(unregistered.getAddress(), false, unregistered.getNonce());
			} else {
				throw new IllegalArgumentException(String.format("unknown particle: %s", particle));
			}
		}

		private static ValidatorRegistrationState initial(RadixAddress address) {
			return new ValidatorRegistrationState(address, false, -1);
		}

		private RadixAddress getAddress() {
			return address;
		}

		private boolean isRegistered() {
			return registered;
		}

		private long getNonce() {
			return nonce;
		}
	}
}
