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

import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.engine.StateReducer;
import com.radixdlt.identifiers.RRI;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Reduces staked tokens particles to total amount staked per node
 */
public final class StakesReducer implements StateReducer<Stakes, StakedTokensParticle> {
    private final RRI stakingToken;
    private final Supplier<Stakes> initial;

    public StakesReducer(RRI stakingToken, Supplier<Stakes> initial) {
        this.stakingToken = Objects.requireNonNull(stakingToken);
        this.initial = Objects.requireNonNull(initial);
    }

    @Override
    public Class<Stakes> stateClass() {
        return Stakes.class;
    }

    @Override
    public Class<StakedTokensParticle> particleClass() {
        return StakedTokensParticle.class;
    }

    @Override
    public Supplier<Stakes> initial() {
        return initial;
    }

    @Override
    public BiFunction<Stakes, StakedTokensParticle, Stakes> outputReducer() {
        return (prev, p) -> {
            if (!p.getTokDefRef().equals(stakingToken)) {
                return prev;
            }

            return prev.add(p.getDelegateAddress().getPublicKey(), p.getAmount());
        };
    }

    @Override
    public BiFunction<Stakes, StakedTokensParticle, Stakes> inputReducer() {
        return (prev, p) -> {
            if (!p.getTokDefRef().equals(stakingToken)) {
                return prev;
            }

            return prev.add(p.getDelegateAddress().getPublicKey(), p.getAmount());
        };
    }
}
