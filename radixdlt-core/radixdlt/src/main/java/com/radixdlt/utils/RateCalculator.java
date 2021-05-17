/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.utils;

import com.google.common.collect.EvictingQueue;

import java.time.Duration;

public final class RateCalculator {
	private final Duration interval;
	private final EvictingQueue<Long> ticks;

	public RateCalculator(Duration interval, int maxEntries) {
		this.interval = interval;
		this.ticks = EvictingQueue.create(maxEntries);
	}

	public void tick() {
		ticks.offer(System.currentTimeMillis());
	}

	public long currentRate() {
		final var minTime = System.currentTimeMillis() - interval.toMillis();
		return ticks.stream()
			.filter(v -> v >= minTime)
			.count();
	}

}
