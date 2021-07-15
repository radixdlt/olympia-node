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
	private final byte typeByte;
	private final Object parsed;
	private final Supplier<ByteBuffer> stateBuf;

	private REStateUpdate(REOp op, SubstateId id, byte typeByte, Object parsed, Supplier<ByteBuffer> stateBuf) {
		Objects.requireNonNull(op);

		this.op = op;
		this.id = id;
		this.typeByte = typeByte;
		this.parsed = parsed;
		this.stateBuf = stateBuf;
	}

	public static REStateUpdate of(REOp op, SubstateId substateId, byte typeByte, Object parsed, Supplier<ByteBuffer> stateBuf) {
		if (op != REOp.DOWN && op != REOp.UP) {
			throw new IllegalArgumentException();
		}
		return new REStateUpdate(op, substateId, typeByte, parsed, stateBuf);
	}

	public byte typeByte() {
		return typeByte;
	}

	public SubstateId getId() {
		return id;
	}

	public ByteBuffer getStateBuf() {
		return stateBuf.get();
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
