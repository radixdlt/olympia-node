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

public final class Size {
	private final long size;

	private Size(long size) {
		this.size = size;
	}

	@JsonCreator
	public static Size create(@JsonProperty(value = "size", required = true) long size) {
		return new Size(size);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof Size)) {
			return false;
		}

		var sizeDTO = (Size) o;
		return size == sizeDTO.size;
	}

	@Override
	public int hashCode() {
		return Objects.hash(size);
	}

	@Override
	public String toString() {
		return "{size:" + size + '}';
	}

	public long getSize() {
		return size;
	}
}
