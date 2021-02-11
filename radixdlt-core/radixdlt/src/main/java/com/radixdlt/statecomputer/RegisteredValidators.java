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
import com.radixdlt.crypto.ECPublicKey;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Wrapper class for registered validators
 */
public final class RegisteredValidators {
    private final ImmutableSet<ECPublicKey> validators;

    private RegisteredValidators(ImmutableSet<ECPublicKey> validators) {
        this.validators = validators;
    }

    public static RegisteredValidators create(ImmutableSet<ECPublicKey> validators) {
        return new RegisteredValidators(validators);
    }

    public static RegisteredValidators create(Stream<ECPublicKey> validators) {
        return new RegisteredValidators(validators.collect(ImmutableSet.toImmutableSet()));
    }

    public static RegisteredValidators create() {
        return new RegisteredValidators(ImmutableSet.of());
    }

    public RegisteredValidators combine(RegisteredValidators v) {
        return new RegisteredValidators(
            ImmutableSet.<ECPublicKey>builder()
                .addAll(this.validators)
                .addAll(v.validators)
                .build()
        );
    }

    public RegisteredValidators add(ECPublicKey validator) {
        if (validators.contains(validator)) {
            return this;
        }

        return new RegisteredValidators(
            ImmutableSet.<ECPublicKey>builder()
                .addAll(validators)
                .add(validator)
                .build()
        );
    }

    public RegisteredValidators remove(ECPublicKey validator) {
        if (!validators.contains(validator)) {
            return this;
        }

        return new RegisteredValidators(
            validators.stream()
                .filter(e -> !e.equals(validator))
                .collect(ImmutableSet.toImmutableSet())
        );
    }

    public ImmutableSet<ECPublicKey> toSet() {
        return validators;
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

        RegisteredValidators other = (RegisteredValidators) o;
        return Objects.equals(this.validators, other.validators);
    }
}
