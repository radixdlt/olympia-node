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

package com.radixdlt.application.system.state;

import com.radixdlt.constraintmachine.Particle;

import java.util.Arrays;

public final class VirtualParent implements Particle {
	private final byte[] data;

	public VirtualParent(byte[] data) {
		this.data = data;
	}

	public byte[] getData() {
		return data;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(data);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof VirtualParent)) {
			return false;
		}

		var other = (VirtualParent) o;
		return Arrays.equals(this.data, other.data);
	}
}
