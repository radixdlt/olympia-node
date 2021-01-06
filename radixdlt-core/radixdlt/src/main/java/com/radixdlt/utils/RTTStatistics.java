/*
 * (C) Copyright 2020 Radix DLT Ltd
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

/**
 * Class to keep track of round-trip-time statistics.
 */
public final class RTTStatistics {
	private double minRTT = Double.MAX_VALUE;
	private double maxRTT = 0.0;
	private double sumRTT = 0.0;
	private double sumSquareRTT = 0.0;
	private long countRTT = 0L;

	/**
	 * Return the minimum seen RTT by the {@link #update(double)} method,
	 * or {@code Double.MAX_VALUE} if {@link #count()} is zero.
	 *
	 * @return The minimum seen RTT value
	 */
	public double min() {
		return this.minRTT;
	}

	/**
	 * Return the maximum seen RTT by the {@link #update(double)} method,
	 * or {@code 0.0} if {@link #count()} is zero.
	 *
	 * @return The maximum seen RTT value
	 */
	public double max() {
		return this.maxRTT;
	}

	/**
	 * Return the mean RTT by the {@link #update(double)} method,
	 * or {@code Double.MAX_VALUE} if {@link #count()} is zero.
	 *
	 * @return The mean RTT values
	 */
	public double mean() {
		if (this.countRTT == 0) {
			return Double.MAX_VALUE;
		}
		return this.sumRTT / this.countRTT;
	}

	/**
	 * Return the second central moment of the RTT values seen by the
	 * {@link #update(double)} method, or {@code Double.MAX_VALUE} if
	 * {@link #count()} is zero.
	 *
	 * @return The second central moment RTT values
	 */
	public double sigma() {
		return Math.sqrt(this.sumSquareRTT / this.countRTT - Math.pow(mean(), 2.0));
	}

	/**
	 * Return the count of the RTT values accumulated so far.
	 *
	 * @return the count of the RTT values accumulated so far
	 */
	public long count() {
		return this.countRTT;
	}

	/**
	 * Update the statistics with a new sample time.  Exact units are left
	 * to the caller to decide.
	 *
	 * @param duration the duration in units specified by the caller
	 */
	public void update(double duration) {
		this.minRTT = Math.min(this.minRTT, duration);
		this.maxRTT = Math.max(this.maxRTT, duration);
		this.sumRTT += duration;
		this.sumSquareRTT += Math.pow(duration, 2.0);
		this.countRTT += 1;
	}

	@Override
	public String toString() {
		return String.format("%s[min %s/max %s/mean %s/sigma %s/count %s]",
			getClass().getSimpleName(), this.min(), this.max(), this.mean(), this.sigma(), this.count());
	}
}