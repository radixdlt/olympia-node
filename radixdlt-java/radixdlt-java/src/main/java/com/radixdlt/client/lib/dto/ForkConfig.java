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

package com.radixdlt.client.lib.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class ForkConfig {
	private final String name;
	private final long epoch;
	private final long ceilingView;

	private ForkConfig(String name, long epoch, long ceilingView) {
		this.name = name;
		this.epoch = epoch;
		this.ceilingView = ceilingView;
	}

	@JsonCreator
	public static ForkConfig create(
		@JsonProperty(value = "name", required = true) String name,
		@JsonProperty(value = "epoch", required = true) long epoch,
		@JsonProperty(value = "ceilingView", required = true) long ceilingView
	) {
		return new ForkConfig(name, epoch, ceilingView);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof ForkConfig)) {
			return false;
		}

		var that = (ForkConfig) o;
		return epoch == that.epoch && ceilingView == that.ceilingView && name.equals(that.name);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, epoch, ceilingView);
	}

	@Override
	public String toString() {
		return "{name:'" + name + '\'' + ", epoch:" + epoch + ", ceilingView:" + ceilingView + '}';
	}

	public String getName() {
		return name;
	}

	public long getEpoch() {
		return epoch;
	}

	public long getCeilingView() {
		return ceilingView;
	}
}
