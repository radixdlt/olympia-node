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

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.validators.ValidatorParticle;
import com.radixdlt.crypto.ECPublicKey;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Wrapper class for registered validators
 */
public final class RegisteredValidators {
    private final Map<ECPublicKey, ValidatorDetails> validators;

    private RegisteredValidators(Map<ECPublicKey, ValidatorDetails> validators) {
        this.validators = validators;
    }

    public static RegisteredValidators create() {
        return new RegisteredValidators(Map.of());
    }

    public RegisteredValidators combine(RegisteredValidators v) {
        var map = ImmutableMap.<ECPublicKey, ValidatorDetails>builder()
            .putAll(validators)
            .putAll(v.validators)
            .build();

        return new RegisteredValidators(map);
    }

    public RegisteredValidators add(ValidatorParticle particle) {
        var validator = particle.getKey();

        if (validators.containsKey(validator)) {
            //TODO: should we merge details???
            return this;
        }

        var map = ImmutableMap.<ECPublicKey, ValidatorDetails>builder()
            .putAll(validators)
            .put(particle.getKey(), ValidatorDetails.fromParticle(particle))
            .build();

        return new RegisteredValidators(map);
    }

    public RegisteredValidators remove(ValidatorParticle particle) {
        var validator = particle.getKey();

        if (!validators.containsKey(validator)) {
            return this;
        }

        return new RegisteredValidators(
            validators.entrySet().stream()
                .filter(e -> !e.getKey().equals(validator))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
        );
    }

    public Set<ECPublicKey> toSet() {
        return Set.copyOf(validators.keySet());
    }

    public <T> List<T> map(BiFunction<ECPublicKey, ValidatorDetails, T> mapper) {
        return validators.entrySet()
            .stream()
            .map(entry -> mapper.apply(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
    }

    @Override
    public int hashCode() {
        return Objects.hash(validators);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RegisteredValidators)) {
            return false;
        }

        var other = (RegisteredValidators) o;
        return Objects.equals(this.validators, other.validators);
    }
}
