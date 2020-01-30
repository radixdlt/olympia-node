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

package com.radixdlt.middleware;

import com.radixdlt.common.Atom;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.engine.RadixEngineAtom;

public final class SimpleRadixEngineAtom implements RadixEngineAtom {
	private final CMInstruction	cmInstruction;
	private final Atom atom;

	public SimpleRadixEngineAtom(Atom atom, CMInstruction cmInstruction) {
		this.atom = atom;
		this.cmInstruction = cmInstruction;
	}

	@Override
	public CMInstruction getCMInstruction() {
		return cmInstruction;
	}

	public Atom getAtom() {
		return atom;
	}
}
