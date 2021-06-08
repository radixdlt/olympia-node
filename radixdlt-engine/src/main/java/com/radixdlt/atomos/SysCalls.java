/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.atomos;

import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.EndProcedure;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.ShutdownAllProcedure;
import com.radixdlt.constraintmachine.UpProcedure;

/**
 * The interface in which a constraint scrypt can be programmed against.
 */
public interface SysCalls {
	/**
	 * Registers a Particle.
	 * This is required for all other system calls using the particle.
	 * @param particleClass The particle class
	 * @param particleDefinition The particle definition
	 * @param <T> The type of the particle
	 */
	<T extends Particle> void registerParticle(Class<T> particleClass, ParticleDefinition<T> particleDefinition);
	<D extends Particle, S extends ReducerState> void createShutDownAllProcedure(ShutdownAllProcedure<D, S> downProcedure);
	<I extends Particle, S extends ReducerState> void createDownProcedure(DownProcedure<I, S> downProcedure);
	<O extends Particle, S extends ReducerState> void createUpProcedure(UpProcedure<S, O> upProcedure);
	<S extends ReducerState> void createEndProcedure(EndProcedure<S> endProcedure);
}
