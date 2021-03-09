/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.application;

import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;

import java.util.function.Function;
import java.util.function.LongFunction;

/**
 * The validator state of a node
 */
public final class ValidatorState {
    private final Long unregisteredNonce;
    private final RegisteredValidatorParticle registeredParticle;

    private ValidatorState(Long unregisteredNonce, RegisteredValidatorParticle registeredParticle) {
        this.unregisteredNonce = unregisteredNonce;
        this.registeredParticle = registeredParticle;
    }

    public static ValidatorState unregistered() {
        return new ValidatorState(0L, null);
    }

    public static ValidatorState unregistered(long nonce) {
        return new ValidatorState(nonce, null);
    }

    public static ValidatorState registered(RegisteredValidatorParticle registeredParticle) {
        return new ValidatorState(null, registeredParticle);
    }

    public boolean isRegistered() {
        return registeredParticle != null;
    }

    public <T> T map(LongFunction<T> unregisteredMapper, Function<RegisteredValidatorParticle, T> registeredMapper) {
        if (unregisteredNonce != null) {
            return unregisteredMapper.apply(unregisteredNonce);
        } else {
            return registeredMapper.apply(registeredParticle);
        }
    }
}
