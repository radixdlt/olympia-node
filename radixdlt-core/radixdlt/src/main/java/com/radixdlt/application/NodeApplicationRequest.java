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
import com.radixdlt.mempool.MempoolAddSuccess;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class NodeApplicationRequest {
	private final List<TxAction> actions;
	private final CompletableFuture<MempoolAddSuccess> completableFuture;

	private NodeApplicationRequest(
		List<TxAction> actions,
		CompletableFuture<MempoolAddSuccess> completableFuture
	) {
		this.actions = actions;
		this.completableFuture = completableFuture;
	}

	public static NodeApplicationRequest create(TxAction action) {
		return create(List.of(action));
	}

	public static NodeApplicationRequest create(List<TxAction> actions) {
		return create(actions, null);
	}

	public static NodeApplicationRequest create(
		List<TxAction> actions,
		CompletableFuture<MempoolAddSuccess> completableFuture
	) {
		Objects.requireNonNull(actions);
		return new NodeApplicationRequest(actions, completableFuture);
	}

	public List<TxAction> getActions() {
		return actions;
	}

	public Optional<CompletableFuture<MempoolAddSuccess>> completableFuture() {
		return Optional.ofNullable(completableFuture);
	}

	@Override
	public String toString() {
		return String.format("%s{%s}", this.getClass().getSimpleName(), this.actions);
	}
}
