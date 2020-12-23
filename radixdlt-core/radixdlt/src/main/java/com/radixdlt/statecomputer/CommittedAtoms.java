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

package com.radixdlt.statecomputer;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.statecomputer.RadixEngineStateComputer.CommittedAtomWithResult;
import java.util.function.Consumer;

/**
 * Temporary class to hold committed results
 * TODO: Remove class
 */
public final class CommittedAtoms {
	private static class CommittedAtomWithResultSuccess implements CommittedAtomWithResult {
		private final CommittedAtom committedAtom;
		private final ImmutableSet<EUID> indicies;

		private CommittedAtomWithResultSuccess(CommittedAtom committedAtom, ImmutableSet<EUID> indicies) {
			this.committedAtom = committedAtom;
			this.indicies = indicies;
		}

		@Override
		public CommittedAtom getCommittedAtom() {
			return committedAtom;
		}

		@Override
		public CommittedAtomWithResult ifSuccess(Consumer<ImmutableSet<EUID>> successConsumer) {
			successConsumer.accept(indicies);
			return this;
		}
	}

	public static CommittedAtomWithResult success(CommittedAtom committedAtom, ImmutableSet<EUID> indicies) {
		return new CommittedAtomWithResultSuccess(committedAtom, indicies);
	}

	private CommittedAtoms() {
		throw new IllegalStateException("Should not be here.");
	}
}
