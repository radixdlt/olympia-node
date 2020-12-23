/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.engine;

import com.google.common.hash.HashCode;
import com.radixdlt.constraintmachine.CMInstruction;
import java.util.Objects;

/**
 * Simple atom used for testing
 */
public final class BaseAtom implements RadixEngineAtom {
	private final CMInstruction cmInstruction;
	private final HashCode witness;

	public BaseAtom(CMInstruction cmInstruction, HashCode witness) {
		this.cmInstruction = Objects.requireNonNull(cmInstruction);
		this.witness = Objects.requireNonNull(witness);
	}

	@Override
	public CMInstruction getCMInstruction() {
		return cmInstruction;
	}

	@Override
	public HashCode getWitness() {
		return witness;
	}
}
