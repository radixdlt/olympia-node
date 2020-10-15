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

package com.radixdlt.constraintmachine;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.crypto.ECDSASignature;
import java.util.Objects;

/**
 * An instruction to be validated by a Constraint Machine
 */
public final class CMInstruction {
	private final ImmutableList<CMMicroInstruction> microInstructions;
	private final ImmutableMap<EUID, ECDSASignature> signatures;

	public CMInstruction(
		ImmutableList<CMMicroInstruction> microInstructions,
		ImmutableMap<EUID, ECDSASignature> signatures
	) {
		this.microInstructions = Objects.requireNonNull(microInstructions);
		this.signatures = Objects.requireNonNull(signatures);
	}

	public ImmutableList<CMMicroInstruction> getMicroInstructions() {
		return microInstructions;
	}

	public ImmutableMap<EUID, ECDSASignature> getSignatures() {
		return signatures;
	}
}
