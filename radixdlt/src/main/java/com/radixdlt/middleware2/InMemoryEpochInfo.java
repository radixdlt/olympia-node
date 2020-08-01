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

package com.radixdlt.middleware2;

import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.epoch.EpochManager.EpochInfoSender;
import com.radixdlt.consensus.epoch.EpochView;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stores epoch info atomically in memory.
 */
public final class InMemoryEpochInfo implements EpochInfoSender {
	private final AtomicReference<EpochView> lastTimeout = new AtomicReference<>();
	private final AtomicReference<EpochView> currentView = new AtomicReference<>(EpochView.of(0L, View.genesis()));

	@Override
	public void sendCurrentView(EpochView epochView) {
		this.currentView.set(epochView);
	}

	public EpochView getCurrentView() {
		return this.currentView.get();
	}

	@Override
	public void sendTimeoutProcessed(EpochView epochView, BFTNode leader) {
		this.lastTimeout.set(epochView);
	}

	public EpochView getLastTimeout() {
		return this.lastTimeout.get();
	}
}
