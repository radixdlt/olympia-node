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
import com.radixdlt.consensus.bft.BFTHighQCUpdate;
import com.radixdlt.consensus.liveness.EpochLocalTimeoutOccurrence;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ProcessWithSystemInfoRunner;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.Objects;
import java.util.Set;

/**
 * Processes system info events
 */
public final class SystemInfoRunner {

	private final Observable<EpochViewUpdate> currentViews;
	private final Set<EventProcessor<EpochViewUpdate>> viewEventProcessors;

	private final Observable<EpochLocalTimeoutOccurrence> timeouts;
	private final Set<EventProcessor<EpochLocalTimeoutOccurrence>> timeoutEventProcessors;

	private final Observable<BFTHighQCUpdate> bftUpdates;
	private final Set<EventProcessor<BFTHighQCUpdate>> bftUpdateProcessors;

	private final Observable<BFTCommittedUpdate> bftCommittedUpdates;
	private final Set<EventProcessor<BFTCommittedUpdate>> committedProcessors;

	@Inject
	public SystemInfoRunner(
		Observable<EpochViewUpdate> currentViews,
		Set<EventProcessor<EpochViewUpdate>> viewEventProcessors,
		Observable<EpochLocalTimeoutOccurrence> timeouts,
		Set<EventProcessor<EpochLocalTimeoutOccurrence>> timeoutEventProcessors,
		Observable<BFTHighQCUpdate> bftUpdates,
		@ProcessWithSystemInfoRunner Set<EventProcessor<BFTHighQCUpdate>> bftUpdateProcessors,
		Observable<BFTCommittedUpdate> bftCommittedUpdates,
		Set<EventProcessor<BFTCommittedUpdate>> committedProcessors
	) {
		this.currentViews = Objects.requireNonNull(currentViews);
		this.viewEventProcessors = Objects.requireNonNull(viewEventProcessors);
		this.timeouts = Objects.requireNonNull(timeouts);
		this.timeoutEventProcessors = Objects.requireNonNull(timeoutEventProcessors);
		this.bftUpdates = Objects.requireNonNull(bftUpdates);
		this.bftUpdateProcessors = Objects.requireNonNull(bftUpdateProcessors);
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

		this.bftUpdates
			.observeOn(Schedulers.io())
			.subscribe(e -> bftUpdateProcessors.forEach(p -> p.process(e)));

		this.bftCommittedUpdates
			.observeOn(Schedulers.io())
			.subscribe(e -> committedProcessors.forEach(p -> p.process(e)));
	}
}
