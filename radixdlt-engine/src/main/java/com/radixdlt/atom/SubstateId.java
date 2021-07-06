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

package com.radixdlt.atom;

import com.radixdlt.constraintmachine.VirtualKey;
import org.bouncycastle.util.encoders.Hex;

import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.Ints;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import static com.radixdlt.crypto.HashUtils.transactionIdHash;

/**
 * The id of a unique substate
 */
public final class SubstateId {
	public static final int BYTES = AID.BYTES + Integer.BYTES;

	private final byte[] idBytes;

	private SubstateId(byte[] idBytes) {
		this.idBytes = Objects.requireNonNull(idBytes);
	}

	public static SubstateId ofSubstate(AID txId, int index) {
		byte[] id = new byte[BYTES];
		txId.copyTo(id, 0);
		Ints.copyTo(index, id, AID.BYTES);
		return new SubstateId(id);
	}

	public static SubstateId ofVirtualSubstate(SubstateId substateId, VirtualKey virtualKey) {
		if (substateId.isVirtual()) {
			throw new IllegalArgumentException();
		}
		byte[] id = new byte[BYTES + virtualKey.key().length];
		var buf = ByteBuffer.wrap(id);
		buf.put(substateId.asBytes());
		buf.put(virtualKey.key());
		return new SubstateId(id);
	}

	public static SubstateId fromBytes(byte[] bytes) {
		return new SubstateId(bytes);
	}

	public static SubstateId fromBuffer(ByteBuffer buf) {
		byte[] id = new byte[BYTES];
		buf.get(id);
		return fromBytes(id);
	}

	public boolean isVirtual() {
		return idBytes.length > BYTES;
	}

	public byte[] asBytes() {
		return idBytes;
	}

	public AID getTxnId() {
		return AID.from(idBytes);
	}

	public Optional<ByteBuffer> getVirtualKey() {
		if (idBytes.length <= BYTES) {
			return Optional.empty();
		}
		var buf = ByteBuffer.wrap(idBytes, BYTES, idBytes.length - BYTES);
		return Optional.of(buf);
	}

	public Optional<Integer> getIndex() {
		return idBytes.length == BYTES
			   ? Optional.of(Ints.fromByteArray(idBytes, AID.BYTES))
			   : Optional.empty();
	}

	@Override
	public String toString() {
		return Hex.toHexString(idBytes);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(idBytes);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof SubstateId)) {
			return false;
		}

		var other = (SubstateId) o;

		return Arrays.equals(this.idBytes, other.idBytes);
	}
}
