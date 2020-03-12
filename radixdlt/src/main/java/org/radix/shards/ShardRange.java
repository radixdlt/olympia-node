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

package org.radix.shards;

import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput.Output;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("radix.shards.range")
public final class ShardRange {
	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("low")
	@DsonOutput(Output.ALL)
	private final long low;

	@JsonProperty("high")
	@DsonOutput(Output.ALL)
	private final long high;


	ShardRange() {
		// For serializer
		this.low = 0L;
		this.high = 0L;
	}

	public ShardRange(long low, long high) {
		if (low > high) {
			throw new IllegalStateException("'low' shard can not be greater than 'high' shard");
		}
		this.low = low;
		this.high = high;
	}

	public long getLow() {
		return this.low;
	}

	public long getHigh() {
		return this.high;
	}

	public long getSpan() {
		return this.high - this.low;
	}

	public boolean intersects(long point) {
		// FIXME: Fix special magic
		if (this.low == 0L && this.high == 0L) {
			return false;
		}
		return point >= this.low && point <= this.high;
	}

	public boolean intersects(ShardRange range) {
		// FIXME: Fix special magic
		if (this.low == 0L && this.high == 0L) {
			return false;
		}
		return range.high >= this.low && range.low <= this.high;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(this.low) * 31 + Long.hashCode(this.high);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof ShardRange) {
			ShardRange other = (ShardRange) obj;
			return this.low == other.low && this.high == other.high;
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[%s -> %s]", getClass().getSimpleName(), this.low, this.high);
	}
}
