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

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;

@SerializerId2("radix.particles.system_particle")
public final class SystemParticle extends Particle {
	@JsonProperty("epoch")
	@DsonOutput(DsonOutput.Output.ALL)
	private final long epoch;

	@JsonProperty("view")
	@DsonOutput(DsonOutput.Output.ALL)
	private final long view;

	@JsonProperty("timestamp")
	@DsonOutput(DsonOutput.Output.ALL)
	private final long timestamp;

	@JsonCreator
	public SystemParticle(
		@JsonProperty("epoch") long epoch,
		@JsonProperty("view") long view,
		@JsonProperty("timestamp") long timestamp
	) {
		this.epoch = epoch;
		this.view = view;
		this.timestamp = timestamp;
	}

	@Override
	public Set<EUID> getDestinations() {
		return ImmutableSet.of();
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

	@Override
	public int hashCode() {
		return Objects.hashCode(epoch, view, timestamp, getDestinations());
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof SystemParticle)) {
			return false;
		}

		SystemParticle other = (SystemParticle) o;
		return this.epoch == other.epoch
			&& this.view == other.view
			&& this.timestamp == other.timestamp
			&& Objects.equal(this.getDestinations(), other.getDestinations());
	}

	@Override
	public String toString() {
		return String.format("%s{epoch=%s view=%s timestamp=%s}", this.getClass().getSimpleName(), epoch, view, timestamp);
	}
}