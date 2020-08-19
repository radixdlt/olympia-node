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
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.statecomputer.RadixEngineStateComputer.CommittedAtomWithResult;
import java.util.function.Consumer;
import java.util.function.Function;

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
		public <T> MaybeSuccessMapped<T> map(Function<ImmutableSet<EUID>, T> successMapper) {
			return errorMapper -> successMapper.apply(indicies);
		}

		@Override
		public CommittedAtomWithResult ifSuccess(Consumer<ImmutableSet<EUID>> successConsumer) {
			successConsumer.accept(indicies);
			return this;
		}

		@Override
		public CommittedAtomWithResult ifError(Consumer<RadixEngineException> errorConsumer) {
			return this;
		}
	}

	private static class CommittedAtomWithResultException implements CommittedAtomWithResult {
		private final CommittedAtom committedAtom;
		private final RadixEngineException exception;

		private CommittedAtomWithResultException(CommittedAtom committedAtom, RadixEngineException exception) {
			this.committedAtom = committedAtom;
			this.exception = exception;
		}

		@Override
		public CommittedAtom getCommittedAtom() {
			return committedAtom;
		}

		@Override
		public <T> MaybeSuccessMapped<T> map(Function<ImmutableSet<EUID>, T> successMapper) {
			return errorMapper -> errorMapper.apply(exception);
		}

		@Override
		public CommittedAtomWithResult ifSuccess(Consumer<ImmutableSet<EUID>> successConsumer) {
			return this;
		}

		@Override
		public CommittedAtomWithResult ifError(Consumer<RadixEngineException> errorConsumer) {
			errorConsumer.accept(exception);
			return this;
		}
	}

	public static CommittedAtomWithResult success(CommittedAtom committedAtom, ImmutableSet<EUID> indicies) {
		return new CommittedAtomWithResultSuccess(committedAtom, indicies);
	}

	public static CommittedAtomWithResult error(CommittedAtom committedAtom, RadixEngineException e) {
		return new CommittedAtomWithResultException(committedAtom, e);
	}

	private CommittedAtoms() {
		throw new IllegalStateException("Should not be here.");
	}
}
