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

package com.radixdlt.consensus.epoch;

import com.radixdlt.consensus.bft.View;
import java.util.Objects;

/**
 * A bft view with it's corresponding epoch
 */
public final class EpochView implements Comparable<EpochView> {
	private final long epoch;
	private final View view;

	public EpochView(long epoch, View view) {
		if (epoch < 0) {
			throw new IllegalArgumentException("epoch must be >= 0");
		}
		this.epoch = epoch;
		this.view = Objects.requireNonNull(view);
	}

	public static EpochView of(long epoch, View view) {
		return new EpochView(epoch, view);
	}

	public long getEpoch() {
		return epoch;
	}

	public View getView() {
		return view;
	}

	@Override
	public String toString() {
		return String.format("%s{epoch=%s view=%s}", this.getClass().getSimpleName(), epoch, view);
	}

	@Override
	public int hashCode() {
		return Objects.hash(epoch, view);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof EpochView)) {
			return false;
		}

		EpochView other = (EpochView) o;
		return other.epoch == this.epoch && Objects.equals(other.view, this.view);
	}

	@Override
	public int compareTo(EpochView o) {
		if (this.epoch != o.epoch) {
			return Long.compare(this.epoch, o.epoch);
		}

		return this.view.compareTo(o.view);
	}
}
