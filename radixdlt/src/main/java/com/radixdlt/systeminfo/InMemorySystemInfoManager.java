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
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.PreparedVertex;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Timeout;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;
import io.reactivex.rxjava3.core.Observable;
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
public final class InMemorySystemInfoManager {

	private static final Logger logger = LogManager.getLogger();

	private final Observable<EpochView> currentViews;
	private final Observable<Timeout> timeouts;
	private final Observable<QuorumCertificate> highQCs;

	private final Queue<VerifiedVertex> vertexRingBuffer;
	private final AtomicReference<Timeout> lastTimeout = new AtomicReference<>();
	private final AtomicReference<EpochView> currentView = new AtomicReference<>(EpochView.of(0L, View.genesis()));
	private final AtomicReference<QuorumCertificate> highQC = new AtomicReference<>();
	private final Observable<BFTCommittedUpdate> bftCommittedUpdates;

	private final long vertexUpdateFrequency;

	public InMemorySystemInfoManager(
		Observable<EpochView> currentViews,
		Observable<Timeout> timeouts,
		Observable<QuorumCertificate> highQCs,
		Observable<BFTCommittedUpdate> bftCommittedUpdates,
		int vertexBufferSize,
		long vertexUpdateFrequency
	) {
		this.currentViews = Objects.requireNonNull(currentViews);
		this.timeouts = Objects.requireNonNull(timeouts);
		this.highQCs = Objects.requireNonNull(highQCs);
		this.bftCommittedUpdates = Objects.requireNonNull(bftCommittedUpdates);
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
		this.currentViews
			.observeOn(Schedulers.io())
			.subscribe(currentView::set);

		this.timeouts
			.observeOn(Schedulers.io())
			.subscribe(lastTimeout::set);

		this.highQCs
			.observeOn(Schedulers.io())
			.subscribe(highQC::set);

		if (this.vertexUpdateFrequency > 0) {
			this.bftCommittedUpdates
				.observeOn(Schedulers.io())
				.concatMap(committed -> Observable.fromStream(committed.getCommitted().stream().map(PreparedVertex::getVertex)))
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

	public List<VerifiedVertex> getCommittedVertices() {
		List<VerifiedVertex> vertices = Lists.newArrayList();
		// Use internal iteration for thread safety
		vertexRingBuffer.forEach(vertices::add);
		return vertices;
	}
}
