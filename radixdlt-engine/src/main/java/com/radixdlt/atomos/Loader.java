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

import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Procedure;

/**
 * The interface in which a constraint scrypt can be programmed against.
 */
public interface Loader {
	/**
	 * Registers a Particle.
	 * This is required for all other system calls using the particle.
	 * @param substateDefinition The particle definition
	 * @param <T> The type of the particle
	 */
	<T extends Particle> void substate(SubstateDefinition<T> substateDefinition);
	void procedure(Procedure procedure);
}
