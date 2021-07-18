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

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class SystemMapKey {
	private final byte[] key;

	private SystemMapKey(byte[] key) {
		this.key = key;
	}


	public static SystemMapKey ofSystem(byte key) {
		return new SystemMapKey(new byte[] {REAddr.REAddrType.SYSTEM.byteValue(), key});
	}

	public static SystemMapKey ofValidatorDataParent(byte typeId) {
		return new SystemMapKey(new byte[] {2, typeId});
	}

	public static SystemMapKey ofValidatorData(byte typeId, byte[] mapKey) {
		var buf = ByteBuffer.allocate(2 + mapKey.length);
		buf.put((byte) 2); // TODO: corresponds to REAddr addressing scheme
		buf.put(typeId);
		buf.put(mapKey);
		return new SystemMapKey(buf.array());
	}

	public static SystemMapKey ofResourceData(REAddr addr, byte typeId) {
		var buf = ByteBuffer.allocate(addr.getBytes().length + 1);
		buf.put(addr.getBytes());
		buf.put(typeId);
		return new SystemMapKey(buf.array());
	}

	public byte[] array() {
		return key;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(key);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof SystemMapKey)) {
			return false;
		}

		var other = (SystemMapKey) o;
		return Arrays.equals(this.key, other.key);
	}
}
