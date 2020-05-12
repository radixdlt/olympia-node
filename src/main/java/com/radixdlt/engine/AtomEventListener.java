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

package com.radixdlt.engine;

import com.radixdlt.identifiers.AID;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.Particle;

/**
 * Listener for atom events as they go through the Radix Engine pipeline.
 */
public interface AtomEventListener<T extends RadixEngineAtom> {
	default void onCMSuccess(T atom) {
	}

	default void onCMError(T atom, CMError error) {
	}

	default void onStateStore(T atom) {
	}

	default void onVirtualStateConflict(T atom, DataPointer issueParticle) {
	}

	default void onStateConflict(T atom, DataPointer issueParticle, T conflictingAtom) {
	}

	default void onStateMissingDependency(AID atomId, Particle particle) {
	}
}
