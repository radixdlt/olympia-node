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

package com.radixdlt.integration.distributed.simulation.monitors.consensus;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.radixdlt.integration.distributed.simulation.Monitor;
import com.radixdlt.integration.distributed.simulation.MonitorKey;
import com.radixdlt.integration.distributed.simulation.TestInvariant;

/**
 * Monitors which checks things in the sync module
 */
public final class SyncMonitors {

	public static Module maxLedgerSyncLag(long maxLag) {
		return new AbstractModule() {
			@ProvidesIntoMap
			@MonitorKey(Monitor.SYNC_MAX_LAG)
			TestInvariant maxSyncLagInvariant() {
				return new MaxLedgerSyncLagInvariant(maxLag);
			}
		};
	}

	private SyncMonitors() {
		throw new IllegalStateException("Cannot instantiate.");
	}
}
