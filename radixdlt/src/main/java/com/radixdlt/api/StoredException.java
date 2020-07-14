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

package com.radixdlt.api;

import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.middleware2.CommittedAtom;
import java.util.Objects;

/**
 * A ledger conflict event
 */
public final class StoredException {
	private final CommittedAtom committedAtom;
	private final RadixEngineException exception;

	public StoredException(CommittedAtom committedAtom, RadixEngineException exception) {
		this.committedAtom = Objects.requireNonNull(committedAtom);
		this.exception = Objects.requireNonNull(exception);
	}

	public CommittedAtom getAtom() {
		return committedAtom;
	}

	public RadixEngineException getException() {
		return exception;
	}

	@Override
	public String toString() {
		return String.format("%s{exception=%s aid=%s meta=%s}",
			this.getClass().getSimpleName(), this.exception, this.committedAtom.getAID(), this.committedAtom.getVertexMetadata()
		);
	}
}
