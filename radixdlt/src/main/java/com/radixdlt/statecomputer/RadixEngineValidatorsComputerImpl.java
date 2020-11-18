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

package com.radixdlt.statecomputer;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.RadixAddress;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Helper class to build validator sets from radix engine state.
 */
@NotThreadSafe
public final class RadixEngineValidatorsComputerImpl implements RadixEngineValidatorsComputer {
	private final ImmutableSet<ECPublicKey> validators;

	private RadixEngineValidatorsComputerImpl(ImmutableSet<ECPublicKey> validators) {
		this.validators = validators;
	}

	public static RadixEngineValidatorsComputer create() {
		return new RadixEngineValidatorsComputerImpl(ImmutableSet.of());
	}

	private RadixEngineValidatorsComputer next(ImmutableSet<ECPublicKey> validators) {
		return new RadixEngineValidatorsComputerImpl(validators);
	}

	@Override
	public RadixEngineValidatorsComputer removeValidator(RadixAddress validatorAddress) {
		final var validatorKey = validatorAddress.getPublicKey();
		if (this.validators.contains(validatorKey)) {
			final var nextValidators = this.validators.stream()
				.filter(e -> !e.equals(validatorKey))
				.collect(ImmutableSet.toImmutableSet());

			return next(nextValidators);
		}
		return this;
	}

	@Override
	public RadixEngineValidatorsComputer addValidator(RadixAddress validatorAddress) {
		final var validatorKey = validatorAddress.getPublicKey();
		if (!this.validators.contains(validatorKey)) {
			final var nextValidators = ImmutableSet.<ECPublicKey>builder()
				.addAll(this.validators)
				.add(validatorKey)
				.build();

			return next(nextValidators);
		}
		return this;
	}

	@Override
	public ImmutableSet<ECPublicKey> activeValidators() {
		return this.validators;
	}
}
