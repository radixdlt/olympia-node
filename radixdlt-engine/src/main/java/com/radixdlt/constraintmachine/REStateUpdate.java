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

/**
 * Instruction which has been parsed and state checked by Radix Engine
 */
public final class REStateUpdate {
	private final REInstruction.REOp op;
	private final Substate substate;
	private final ByteBuffer stateBuf;

	private REStateUpdate(REInstruction.REOp op, Substate substate, ByteBuffer stateBuf) {
		Objects.requireNonNull(op);
		Objects.requireNonNull(substate);

		this.op = op;
		this.substate = substate;
		this.stateBuf = stateBuf;
	}

	public static REStateUpdate of(REInstruction.REOp op, Substate substate, ByteBuffer stateBuf) {
		return new REStateUpdate(op, substate, stateBuf);
	}

	public ByteBuffer getStateBuf() {
		return stateBuf;
	}

	public REInstruction.REOp getOp() {
		return op;
	}

	public Spin getCheckSpin() {
		return op.getCheckSpin();
	}

	public Spin getNextSpin() {
		return op.getNextSpin();
	}

	public boolean isBootUp() {
		return this.op.getNextSpin() == Spin.UP;
	}

	public boolean isShutDown() {
		return this.op.getNextSpin() == Spin.DOWN;
	}

	public Substate getSubstate() {
		return substate;
	}

	public Particle getParticle() {
		return substate.getParticle();
	}

	public <T extends Particle> T getParticle(Class<T> cls) {
		return cls.cast(substate.getParticle());
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), substate);
	}
}
