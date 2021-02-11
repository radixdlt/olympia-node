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
 */

package com.radixdlt.statecomputer;

import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.engine.StateReducer;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Reducer to keep track of the last system particle
 */
public final class LastSystemParticleReducer implements StateReducer<SystemParticle, SystemParticle> {
    @Override
    public Class<SystemParticle> stateClass() {
        return SystemParticle.class;
    }

    @Override
    public Class<SystemParticle> particleClass() {
        return SystemParticle.class;
    }

    @Override
    public Supplier<SystemParticle> initial() {
        return () -> new SystemParticle(0, 0, 0);
    }

    @Override
    public BiFunction<SystemParticle, SystemParticle, SystemParticle> outputReducer() {
        return (prev, p) -> p;
    }

    @Override
    public BiFunction<SystemParticle, SystemParticle, SystemParticle> inputReducer() {
        return (prev, p) -> prev;
    }
}
