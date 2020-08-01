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

import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stores info atomically in memory.
 */
public final class InMemoryInfoStateRunner {
	private final InfoRx infoRx;
	private final AtomicReference<Timeout> lastTimeout = new AtomicReference<>();
	private final AtomicReference<EpochView> currentView = new AtomicReference<>(EpochView.of(0L, View.genesis()));

	public InMemoryInfoStateRunner(
		InfoRx infoRx
	) {
		this.infoRx = infoRx;
	}

	public void start() {
		this.infoRx.currentViews().subscribe(currentView::set);
		this.infoRx.timeouts().subscribe(lastTimeout::set);
	}

	public EpochView getCurrentView() {
		return this.currentView.get();
	}

	public Timeout getLastTimeout() {
		return this.lastTimeout.get();
	}
}
