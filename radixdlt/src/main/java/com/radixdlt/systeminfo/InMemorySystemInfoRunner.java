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

import com.google.inject.Inject;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Timeout;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.environment.EventProcessor;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.Objects;
import java.util.Set;

/**
 * Processes system info events
 */
public final class InMemorySystemInfoRunner {

	private final Observable<EpochView> currentViews;
	private final Observable<Timeout> timeouts;
	private final Observable<QuorumCertificate> highQCs;
	private final Observable<BFTCommittedUpdate> bftCommittedUpdates;
	private final Set<EventProcessor<EpochView>> viewEventProcessors;
	private final Set<EventProcessor<Timeout>> timeoutEventProcessors;
	private final Set<EventProcessor<QuorumCertificate>> highQCProcessors;
	private final Set<EventProcessor<BFTCommittedUpdate>> committedProcessors;

	@Inject
	public InMemorySystemInfoRunner(
		Observable<EpochView> currentViews,
		Set<EventProcessor<EpochView>> viewEventProcessors,
		Observable<Timeout> timeouts,
		Set<EventProcessor<Timeout>> timeoutEventProcessors,
		Observable<QuorumCertificate> highQCs,
		Set<EventProcessor<QuorumCertificate>> highQCProcessors,
		Observable<BFTCommittedUpdate> bftCommittedUpdates,
		Set<EventProcessor<BFTCommittedUpdate>> committedProcessors
	) {
		this.currentViews = Objects.requireNonNull(currentViews);
		this.viewEventProcessors = Objects.requireNonNull(viewEventProcessors);
		this.timeouts = Objects.requireNonNull(timeouts);
		this.timeoutEventProcessors = Objects.requireNonNull(timeoutEventProcessors);
		this.highQCs = Objects.requireNonNull(highQCs);
		this.highQCProcessors = Objects.requireNonNull(highQCProcessors);
		this.bftCommittedUpdates = Objects.requireNonNull(bftCommittedUpdates);
		this.committedProcessors = Objects.requireNonNull(committedProcessors);
	}

	public void start() {
		this.currentViews
			.observeOn(Schedulers.io())
			.subscribe(e -> viewEventProcessors.forEach(p -> p.process(e)));

		this.timeouts
			.observeOn(Schedulers.io())
			.subscribe(e -> timeoutEventProcessors.forEach(p -> p.process(e)));

		this.highQCs
			.observeOn(Schedulers.io())
			.subscribe(e -> highQCProcessors.forEach(p -> p.process(e)));

		this.bftCommittedUpdates
			.observeOn(Schedulers.io())
			.subscribe(e -> committedProcessors.forEach(p -> p.process(e)));
	}
}
