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

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ReducerResult {
	private final ReducerState reducerState;
	private final TxAction txAction;

	private ReducerResult(ReducerState reducerState, TxAction txAction) {
		this.reducerState = reducerState;
		this.txAction = txAction;
	}

	public static ReducerResult incomplete(ReducerState reducerState) {
		return new ReducerResult(reducerState, null);
	}

	public static ReducerResult incomplete(ReducerState reducerState, TxAction txAction) {
		return new ReducerResult(reducerState, txAction);
	}

	public static ReducerResult complete() {
		return new ReducerResult(null, null);
	}

	public static ReducerResult complete(TxAction txAction) {
		return new ReducerResult(null, txAction);
	}

	public void ifCompleteElse(
		Consumer<Optional<TxAction>> completeConsumer,
		BiConsumer<ReducerState, Optional<TxAction>> incompleteConsumer
	) {
		if (reducerState == null) {
			completeConsumer.accept(Optional.ofNullable(txAction));
		} else {
			incompleteConsumer.accept(reducerState, Optional.ofNullable(txAction));
		}
	}
}
