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

public final class TimeDTO {
	private final long time;

	private TimeDTO(long time) {
		this.time = time;
	}

	@JsonCreator
	public static TimeDTO create(@JsonProperty(value = "time", required = true) long time) {
		return new TimeDTO(time);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof TimeDTO)) {
			return false;
		}

		var timeDTO = (TimeDTO) o;
		return time == timeDTO.time;
	}

	@Override
	public int hashCode() {
		return Objects.hash(time);
	}

	@Override
	public String toString() {
		return "{time:" + time + '}';
	}

	public long getTime() {
		return time;
	}
}
