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

public class ReadWriteStats {
	private final long read;
	private final long write;
	private final long total;
	private final ReadWrite bytes;

	private ReadWriteStats(long read, long write, long total, ReadWrite bytes) {
		this.read = read;
		this.write = write;
		this.total = total;
		this.bytes = bytes;
	}

	@JsonCreator
	public static ReadWriteStats create(
		@JsonProperty(value = "read", required = true) long read,
		@JsonProperty(value = "write", required = true) long write,
		@JsonProperty(value = "total", required = true) long total,
		@JsonProperty(value = "bytes", required = true) ReadWrite bytes
	) {
		return new ReadWriteStats(read, write, total, bytes);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof ReadWriteStats)) {
			return false;
		}

		var that = (ReadWriteStats) o;
		return read == that.read && write == that.write && total == that.total && bytes.equals(that.bytes);
	}

	@Override
	public int hashCode() {
		return Objects.hash(read, write, total, bytes);
	}

	@Override
	public String toString() {
		return "{read:" + read + ", write:" + write + ", total:" + total + ", bytes:" + bytes + '}';
	}

	public long getRead() {
		return read;
	}

	public long getWrite() {
		return write;
	}

	public long getTotal() {
		return total;
	}

	public ReadWrite getBytes() {
		return bytes;
	}
}
