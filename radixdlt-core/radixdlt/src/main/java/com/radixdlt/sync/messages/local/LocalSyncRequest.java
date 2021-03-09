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
 */

package com.radixdlt.sync.messages.local;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import java.util.Objects;

/**
 * A request to sync ledger to a given version.
 */
public final class LocalSyncRequest {

	private final VerifiedLedgerHeaderAndProof target;
	private final ImmutableList<BFTNode> targetNodes;

	public LocalSyncRequest(VerifiedLedgerHeaderAndProof target, ImmutableList<BFTNode> targetNodes) {
		this.target = Objects.requireNonNull(target);
		this.targetNodes = Objects.requireNonNull(targetNodes);
	}

	public VerifiedLedgerHeaderAndProof getTarget() {
		return target;
	}

	public ImmutableList<BFTNode> getTargetNodes() {
		return targetNodes;
	}

	@Override
	public String toString() {
		return String.format("%s {%s target=%s}", this.getClass().getSimpleName(), target, targetNodes);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		LocalSyncRequest that = (LocalSyncRequest) o;
		return Objects.equals(target, that.target) && Objects.equals(targetNodes, that.targetNodes);
	}

	@Override
	public int hashCode() {
		return Objects.hash(target, targetNodes);
	}
}
