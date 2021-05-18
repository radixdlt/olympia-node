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

package com.radixdlt.atommodel.system;

import com.google.common.base.Objects;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.crypto.ECPublicKey;

import java.time.Instant;

public final class SystemParticle implements Particle {
	private final long epoch;
	private final long view;
	private final long timestamp;
	private final ECPublicKey leader;

	public SystemParticle(long epoch, long view, long timestamp, ECPublicKey leader) {
		this.epoch = epoch;
		this.view = view;
		this.leader = leader;
		this.timestamp = timestamp;
	}

	public ECPublicKey getLeader() {
		return leader;
	}

	public long getEpoch() {
		return epoch;
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
		return Objects.hashCode(epoch, view, leader, timestamp);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof SystemParticle)) {
			return false;
		}

		var other = (SystemParticle) o;

		return this.epoch == other.epoch
			&& this.view == other.view
			&& Objects.equal(this.leader, other.leader)
			&& this.timestamp == other.timestamp;
	}

	@Override
	public String toString() {
		return String.format("%s{epoch=%s view=%s timestamp=%s leader=%s}",
			this.getClass().getSimpleName(), epoch, view, timestamp, leader
		);
	}
}