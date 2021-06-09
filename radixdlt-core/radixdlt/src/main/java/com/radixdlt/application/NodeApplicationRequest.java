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
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.mempool.MempoolAddSuccess;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class NodeApplicationRequest {
	private final TxnConstructionRequest request;
	private final CompletableFuture<MempoolAddSuccess> completableFuture;

	private NodeApplicationRequest(
		TxnConstructionRequest request,
		CompletableFuture<MempoolAddSuccess> completableFuture
	) {
		this.request = request;
		this.completableFuture = completableFuture;
	}

	public static NodeApplicationRequest create(TxAction action) {
		return create(TxnConstructionRequest.create().action(action));
	}

	public static NodeApplicationRequest create(TxnConstructionRequest request) {
		return create(request, null);
	}

	public static NodeApplicationRequest create(
		TxnConstructionRequest request,
		CompletableFuture<MempoolAddSuccess> completableFuture
	) {
		Objects.requireNonNull(request);
		return new NodeApplicationRequest(request, completableFuture);
	}

	public TxnConstructionRequest getRequest() {
		return request;
	}

	public Optional<CompletableFuture<MempoolAddSuccess>> completableFuture() {
		return Optional.ofNullable(completableFuture);
	}

	@Override
	public String toString() {
		return String.format("%s{%s}", this.getClass().getSimpleName(), this.request);
	}
}
