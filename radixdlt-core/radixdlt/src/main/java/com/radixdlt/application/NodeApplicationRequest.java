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

package com.radixdlt.application;

import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.Txn;
import com.radixdlt.identifiers.AID;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class NodeApplicationRequest {
	private final List<TxAction> actions;
	private final Consumer<AID> onSuccess;
	private final BiConsumer<Txn, String> onError;

	private NodeApplicationRequest(
		List<TxAction> actions,
		Consumer<AID> onSuccess,
		BiConsumer<Txn, String> onError
	) {
		this.actions = actions;
		this.onSuccess = onSuccess;
		this.onError = onError;
	}

	public static NodeApplicationRequest create(TxAction action) {
		return create(List.of(action));
	}

	public static NodeApplicationRequest create(List<TxAction> actions) {
		return create(actions, aid -> { }, (txn, error) -> { });
	}

	public static NodeApplicationRequest create(
		List<TxAction> actions,
		Consumer<AID> onSuccess,
		BiConsumer<Txn, String> onError
	) {
		Objects.requireNonNull(actions);
		Objects.requireNonNull(onSuccess);
		Objects.requireNonNull(onError);
		return new NodeApplicationRequest(actions, onSuccess, onError);
	}

	public List<TxAction> getActions() {
		return actions;
	}

	public void onSuccess(AID aid) {
		onSuccess.accept(aid);
	}

	public void onFailure(Txn txn, String errorMessage) {
		onError.accept(txn, errorMessage);
	}
}
