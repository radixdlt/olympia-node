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

	private static SystemMapKey of(REAddr addr, byte key) {
		var buf = ByteBuffer.allocate(addr.getBytes().length + 1);
		buf.put(addr.getBytes());
		buf.put(key);
		return new SystemMapKey(buf.array());
	}

	private static SystemMapKey of(REAddr addr, byte key0, byte[] key1) {
		var buf = ByteBuffer.allocate(addr.getBytes().length + 1 + key1.length);
		buf.put(addr.getBytes());
		buf.put(key0);
		buf.put(key1);
		return new SystemMapKey(buf.array());
	}

	public static SystemMapKey ofSystem(byte key) {
		return of(REAddr.ofSystem(), key);
	}

	public static SystemMapKey ofSystem(byte typeId, byte[] mapKey) {
		return of(REAddr.ofSystem(), typeId, mapKey);
	}

	public static SystemMapKey ofResourceData(REAddr addr, byte typeId) {
		return of(addr, typeId);
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
