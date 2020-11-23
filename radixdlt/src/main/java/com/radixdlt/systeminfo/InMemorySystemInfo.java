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

package com.radixdlt.systeminfo;

import com.google.common.collect.EvictingQueue;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.LocalTimeoutOccurrence;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.PreparedVertex;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages system information to be consumed by clients such as the api.
 */
public final class InMemorySystemInfo {
	private final Queue<VerifiedVertex> vertexRingBuffer;
	private final AtomicReference<LocalTimeoutOccurrence> lastTimeout = new AtomicReference<>();
	private final AtomicReference<EpochView> currentView = new AtomicReference<>(EpochView.of(0L, View.genesis()));
	private final AtomicReference<QuorumCertificate> highQC = new AtomicReference<>();

	public InMemorySystemInfo(int vertexBufferSize) {
		if (vertexBufferSize < 0) {
			throw new IllegalArgumentException("vertexBufferSize must be >= 0 but was " + vertexBufferSize);
		}
		this.vertexRingBuffer = Queues.synchronizedQueue(EvictingQueue.create(vertexBufferSize));
	}

	public void processTimeout(LocalTimeoutOccurrence timeout) {
		lastTimeout.set(timeout);
	}

	public void processView(EpochView epochView) {
		currentView.set(epochView);
	}

	public void processHighQC(QuorumCertificate qc) {
		this.highQC.set(qc);
	}

	public void processCommitted(BFTCommittedUpdate committedUpdate) {
		committedUpdate.getCommitted().stream().map(PreparedVertex::getVertex).forEach(this.vertexRingBuffer::add);
	}


	public EpochView getCurrentView() {
		return this.currentView.get();
	}

	public LocalTimeoutOccurrence getLastTimeout() {
		return this.lastTimeout.get();
	}

	public QuorumCertificate getHighestQC() {
		return this.highQC.get();
	}

	public List<VerifiedVertex> getCommittedVertices() {
		List<VerifiedVertex> vertices = Lists.newArrayList();
		// Use internal iteration for thread safety
		vertexRingBuffer.forEach(vertices::add);
		return vertices;
	}
}
