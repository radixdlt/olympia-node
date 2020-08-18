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

package com.radixdlt.integration.distributed.deterministic.configuration;

import java.util.Objects;
import com.radixdlt.utils.UInt256;

/**
 * A node index, together with its weight.
 */
public final class NodeIndexAndWeight {
	private final int index;
	private final UInt256 weight;

	private NodeIndexAndWeight(int index, UInt256 weight) {
		this.index = index;
		this.weight = Objects.requireNonNull(weight);
	}

	/**
	 * Returns a {@code NodeIndexAndWeight} from specified values.
	 */
	public static NodeIndexAndWeight from(int index, UInt256 weight) {
		return new NodeIndexAndWeight(index, weight);
	}

	public int index() {
		return this.index;
	}

	public UInt256 weight() {
		return this.weight;
	}

	@Override
	public int hashCode() {
		return weight.hashCode() * 31 + this.index;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof NodeIndexAndWeight) {
			NodeIndexAndWeight that = (NodeIndexAndWeight) o;
			return this.index == that.index && this.weight.equals(that.weight);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s]", getClass().getSimpleName(), this.index, this.weight);
	}
}
