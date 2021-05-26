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

package com.radixdlt.atommodel.system.state;

import com.google.common.base.Objects;
import com.radixdlt.constraintmachine.Particle;

import java.time.Instant;

public final class RoundData implements Particle {
	private final long view;
	private final long timestamp;


	public RoundData(long view, long timestamp) {
		this.view = view;
		this.timestamp = timestamp;
	}

	public long getView() {
		return view;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public Instant asInstant() {
		return Instant.ofEpochMilli(timestamp);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(view, timestamp);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RoundData)) {
			return false;
		}

		var other = (RoundData) o;

		return this.view == other.view
			&& this.timestamp == other.timestamp;
	}

	@Override
	public String toString() {
		return String.format("%s{view=%s timestamp=%s}", this.getClass().getSimpleName(), view, timestamp);
	}
}
