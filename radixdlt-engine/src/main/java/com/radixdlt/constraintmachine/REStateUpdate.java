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

import com.radixdlt.atom.SubstateId;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Instruction which has been parsed and state checked by Radix Engine
 */
public final class REStateUpdate {
	private final REOp op;
	private final SubstateId id;
	private final Supplier<ByteBuffer> stateBuf;
	private final Object parsed;

	private REStateUpdate(REOp op, SubstateId id, Supplier<ByteBuffer> stateBuf, Object parsed) {
		Objects.requireNonNull(op);

		this.op = op;
		this.id = id;
		this.stateBuf = stateBuf;
		this.parsed = parsed;
	}

	public static REStateUpdate of(REOp op, SubstateId substateId, Supplier<ByteBuffer> stateBuf, Object parsed) {
		if (op != REOp.DOWN && op != REOp.UP && op != REOp.VUP) {
			throw new IllegalArgumentException();
		}
		return new REStateUpdate(op, substateId, stateBuf, parsed);
	}

	public SubstateId getId() {
		return id;
	}

	public ByteBuffer getStateBuf() {
		return stateBuf.get();
	}

	public boolean isVirtualBootUp() {
		return this.op == REOp.VUP;
	}

	public boolean isBootUp() {
		return this.op == REOp.UP;
	}

	public boolean isShutDown() {
		return this.op == REOp.DOWN;
	}

	public Object getParsed() {
		return parsed;
	}

	public RawSubstateBytes getRawSubstateBytes() {
		var buffer = stateBuf.get();
		int remaining = buffer.remaining();
		var buf = new byte[remaining];
		buffer.get(buf);
		return new RawSubstateBytes(id.asBytes(), buf);
	}

	@Override
	public String toString() {
		return String.format("%s{op=%s state=%s}", getClass().getSimpleName(), op, parsed);
	}
}
