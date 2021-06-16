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

package com.radixdlt.constraintmachine;

import com.radixdlt.identifiers.REAddr;

import java.util.Objects;

public final class OpSignature {
	private final REOp op;
	private final Object type;

	private OpSignature(REOp op, Object type) {
		this.op = op;
		this.type = type;
	}

	public REOp op() {
		return op;
	}

	public static OpSignature ofSubstateUpdate(REOp op, Class<? extends Particle> particleClass) {
		return new OpSignature(op, particleClass);
	}

	public static OpSignature ofMethod(REOp op, REAddr addr) {
		return new OpSignature(op, addr);
	}

	@Override
	public int hashCode() {
		return Objects.hash(op, type);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof OpSignature)) {
			return false;
		}

		var other = (OpSignature) o;
		return Objects.equals(this.op, other.op)
			&& Objects.equals(this.type, other.type);
	}

	@Override
	public String toString() {
		return String.format("%s{op=%s type=%s}", this.getClass().getSimpleName(), this.op, this.type);
	}
}
