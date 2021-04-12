/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.constraintmachine;

import com.radixdlt.atom.TxAction;
import com.radixdlt.utils.Pair;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ReducerResult {
	private final ReducerState reducerState;
	private final boolean keepInput;
	private final TxAction txAction;

	private ReducerResult(ReducerState reducerState, boolean keepInput, TxAction txAction) {
		this.reducerState = reducerState;
		this.keepInput = keepInput;
		this.txAction = txAction;
	}

	public static ReducerResult incomplete(ReducerState reducerState, boolean keepInput) {
		return new ReducerResult(reducerState, keepInput, null);
	}

	public static ReducerResult complete(TxAction txAction) {
		return new ReducerResult(null, false, txAction);
	}

	public boolean isComplete() {
		return reducerState == null;
	}

	public void ifIncompleteElse(BiConsumer<Boolean, ReducerState> onIncomplete, Consumer<TxAction> onComplete) {
		if (reducerState != null) {
			onIncomplete.accept(keepInput, reducerState);
		} else {
			onComplete.accept(txAction);
		}
	}

	public Optional<Pair<Boolean, ReducerState>> getIncomplete() {
		if (reducerState == null) {
			return Optional.empty();
		} else {
			return Optional.of(Pair.of(keepInput, reducerState));
		}
	}
}
