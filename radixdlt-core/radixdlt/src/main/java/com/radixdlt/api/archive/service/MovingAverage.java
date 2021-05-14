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

package com.radixdlt.api.archive.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class MovingAverage {
	private final long averagingFactor;
	private BigDecimal average = BigDecimal.ZERO;
	private long count = 0;

	private MovingAverage(long averagingFactor) {
		this.averagingFactor = averagingFactor;
	}

	public static MovingAverage create(long averagingFactor) {
		if (averagingFactor <= 1) {
			throw new IllegalArgumentException("Averaging factor must be above 1");
		}
		return new MovingAverage(averagingFactor);
	}

	public BigDecimal asBigDecimal() {
		return average;
	}

	public int asInteger() {
		return average.intValue();
	}

	public long asLong() {
		return average.longValue();
	}

	public double asDouble() {
		return average.doubleValue();
	}

	public MovingAverage update(int value) {
		return update(BigDecimal.valueOf(value));
	}

	public MovingAverage update(long value) {
		return update(BigDecimal.valueOf(value));
	}

	public MovingAverage update(double value) {
		return update(BigDecimal.valueOf(value));
	}

	public MovingAverage update(BigDecimal value) {
		count++;

		var divisor = BigDecimal.valueOf(Math.min(count, averagingFactor));
		var delta = value.subtract(average).divide(divisor, 3, RoundingMode.HALF_UP);

		average = average.add(delta);
		return this;
	}
}
