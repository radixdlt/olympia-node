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

import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.Particle;

/**
 * Listener for atom events as they go through the Radix Engine pipeline.
 */
public interface AtomEventListener {
	default void onCMSuccess(Atom atom) {
	}

	default void onCMError(Atom atom, CMError error) {
	}

	default void onStateStore(Atom atom) {
	}

	default void onVirtualStateConflict(Atom atom, DataPointer issueParticle) {
	}

	default void onStateConflict(Atom atom, DataPointer issueParticle, Atom conflictingAtom) {
	}

	default void onStateMissingDependency(AID atomId, Particle particle) {
	}
}
