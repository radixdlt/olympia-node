/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.statecomputer;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.atommodel.validators.ValidatorParticle;
import com.radixdlt.crypto.ECPublicKey;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Wrapper class for registered validators
 */
public final class RegisteredValidators {
    private final Set<ValidatorParticle> validatorParticles;

    private RegisteredValidators(Set<ValidatorParticle> validatorParticles) {
        this.validatorParticles = validatorParticles;
    }

    public static RegisteredValidators create() {
        return new RegisteredValidators(Set.of());
    }

    public RegisteredValidators add(ValidatorParticle particle) {
        var map = ImmutableSet.<ValidatorParticle>builder()
            .addAll(validatorParticles)
            .add(particle)
            .build();

        return new RegisteredValidators(map);
    }

    public RegisteredValidators remove(ValidatorParticle particle) {
        return new RegisteredValidators(
            validatorParticles.stream()
                .filter(e -> !e.equals(particle))
                .collect(Collectors.toSet())
        );
    }

    public Set<ECPublicKey> toSet() {
    	return validatorParticles.stream()
            .map(ValidatorParticle::getKey)
            .collect(Collectors.toSet());
    }

    public <T> List<T> map(BiFunction<ECPublicKey, ValidatorDetails, T> mapper) {
        return validatorParticles
            .stream()
			.map(p -> mapper.apply(p.getKey(), ValidatorDetails.fromParticle(p)))
            .collect(Collectors.toList());
    }

    @Override
    public int hashCode() {
        return Objects.hash(validatorParticles);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RegisteredValidators)) {
            return false;
        }

        var other = (RegisteredValidators) o;
        return Objects.equals(this.validatorParticles, other.validatorParticles);
    }
}
