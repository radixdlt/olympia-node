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

package com.radixdlt.api;

import com.google.common.collect.EvictingQueue;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Stores info atomically in memory.
 */
public final class InMemoryInfoStateManager {

	private static final Logger logger = LogManager.getLogger();

	private final InfoRx infoRx;

	private final Queue<Vertex> vertexRingBuffer;
	private final AtomicReference<Timeout> lastTimeout = new AtomicReference<>();
	private final AtomicReference<EpochView> currentView = new AtomicReference<>(EpochView.of(0L, View.genesis()));
	private final AtomicReference<QuorumCertificate> highQC = new AtomicReference<>();

	private final long vertexUpdateFrequency;

	public InMemoryInfoStateManager(
		InfoRx infoRx,
		int vertexBufferSize,
		long vertexUpdateFrequency
	) {
		this.infoRx = Objects.requireNonNull(infoRx);
		if (vertexBufferSize < 0) {
			throw new IllegalArgumentException("vertexBufferSize must be >= 0 but was " + vertexBufferSize);
		}
		this.vertexRingBuffer = Queues.synchronizedQueue(EvictingQueue.create(vertexBufferSize));
		if (vertexUpdateFrequency < 0) {
			throw new IllegalArgumentException("vertexUpdateFrequency must be >= 0 but was " + vertexUpdateFrequency);
		}
		this.vertexUpdateFrequency = vertexUpdateFrequency;
		logger.debug("Vertex buffer size {}, frequency {} views", vertexBufferSize, vertexUpdateFrequency);

	}

	public void start() {
		this.infoRx.currentViews()
			.observeOn(Schedulers.io())
			.subscribe(currentView::set);
		this.infoRx.timeouts()
			.observeOn(Schedulers.io())
			.subscribe(lastTimeout::set);
		this.infoRx.highQCs()
			.observeOn(Schedulers.io())
			.subscribe(highQC::set);

		if (this.vertexUpdateFrequency > 0) {
			this.infoRx.committedVertices()
				.observeOn(Schedulers.io())
				.filter(v -> (v.getView().number() % vertexUpdateFrequency) == 0)
				.subscribe(vertexRingBuffer::add);
		}
	}

	public EpochView getCurrentView() {
		return this.currentView.get();
	}

	public Timeout getLastTimeout() {
		return this.lastTimeout.get();
	}

	public QuorumCertificate getHighestQC() {
		return this.highQC.get();
	}

	public List<Vertex> getCommittedVertices() {
		List<Vertex> vertices = Lists.newArrayList();
		// Use internal iteration for thread safety
		vertexRingBuffer.forEach(vertices::add);
		return vertices;
	}
}
