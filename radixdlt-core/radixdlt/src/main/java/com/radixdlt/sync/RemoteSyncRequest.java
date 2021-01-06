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

package com.radixdlt.sync;

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import java.util.Objects;

/**
 * A sync request from a peer
 */
public final class RemoteSyncRequest {
	private final DtoLedgerHeaderAndProof currentHeader;
	private final BFTNode node;

	public RemoteSyncRequest(BFTNode node, DtoLedgerHeaderAndProof currentHeader) {
		this.node = Objects.requireNonNull(node);
		this.currentHeader = currentHeader;
	}

	public BFTNode getNode() {
		return node;
	}

	public DtoLedgerHeaderAndProof getCurrentHeader() {
		return currentHeader;
	}

	@Override
	public String toString() {
		return String.format("%s{current=%s}", this.getClass().getSimpleName(), currentHeader);
	}
}
