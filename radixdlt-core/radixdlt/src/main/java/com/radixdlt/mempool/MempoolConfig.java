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

package com.radixdlt.mempool;

/**
 * Configuration parameters for mempool.
 */
public interface MempoolConfig {
	static MempoolConfig of(long maxSize, long throttleMs) {
		return of(maxSize, throttleMs, 60000L, 60000L, 100);
	}

	static MempoolConfig of(
		long maxSize,
		long throttleMs,
		long commandRelayInitialDelay,
		long commandRelayRepeatDelay,
		int relayMaxPeers
	) {
		return new MempoolConfig() {

			@Override
			public long maxSize() {
				return maxSize;
			}

			@Override
			public long throttleMs() {
				return throttleMs;
			}

			@Override
			public long commandRelayInitialDelay() {
				return commandRelayInitialDelay;
			}

			@Override
			public long commandRelayRepeatDelay() {
				return commandRelayRepeatDelay;
			}

			@Override
			public int relayMaxPeers() {
				return relayMaxPeers;
			}
		};
	}

	/**
	 * Maximum number of commands that a mempool can store.
	 */
	long maxSize();

	/**
	The amount of time in milliseconds to throttle mempool additions
	 */
	long throttleMs();

	/**
	 * Specifies how long a command needs to stay in a mempool in order to be relayed to other peers.
	 */
	long commandRelayInitialDelay();

	/**
	 * Specifies how often command is re-relayed once it's eligible for relay as per commandRelayInitialDelay().
	 */
	long commandRelayRepeatDelay();

	/**
	 * Maximum numbers of peers to relay the command to.
	 */
	int relayMaxPeers();
}
