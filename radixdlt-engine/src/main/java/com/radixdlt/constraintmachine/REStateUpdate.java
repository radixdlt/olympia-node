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

import com.radixdlt.atom.Substate;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;

/**
 * Instruction which has been parsed and state checked by Radix Engine
 */
public final class REStateUpdate {
	private final REOp op;
	private final Substate substate;
	private final byte[] arg;
	private final ByteBuffer stateBuf;

	private REStateUpdate(REOp op, Substate substate, byte[] arg, ByteBuffer stateBuf) {
		Objects.requireNonNull(op);
		Objects.requireNonNull(substate);

		this.op = op;
		this.substate = substate;
		this.arg = arg;
		this.stateBuf = stateBuf;
	}

	public static REStateUpdate of(REOp op, Substate substate, byte[] arg, ByteBuffer stateBuf) {
		return new REStateUpdate(op, substate, arg, stateBuf);
	}

	public ByteBuffer getStateBuf() {
		return stateBuf;
	}

	public Optional<byte[]> getArg() {
		return Optional.ofNullable(arg);
	}

	public REOp getOp() {
		return op;
	}

	public boolean isBootUp() {
		return this.op == REOp.UP;
	}

	public boolean isShutDown() {
		return this.op == REOp.DOWN;
	}

	public Substate getSubstate() {
		return substate;
	}

	public Particle getRawSubstate() {
		return substate.getParticle();
	}

	@Override
	public String toString() {
		return String.format("%s{op=%s state=%s}", getClass().getSimpleName(), op, substate);
	}
}
