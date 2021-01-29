/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.store.mvstore;

import com.google.common.primitives.UnsignedBytes;
import org.h2.mvstore.DataUtils;
import org.h2.mvstore.WriteBuffer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class KeyList {
	private static final int INITIAL_SIZE = 4096;
	private final List<byte[]> entries = new ArrayList<>();

	private KeyList() {
	}

	public static KeyList of(byte[]... entries) {
		var result = new KeyList();

		result.entries.addAll(List.of(entries));
		result.entries.sort(UnsignedBytes.lexicographicalComparator());
		return result;
	}

	public static byte[] merge(byte[] firstValue, byte[] secondValue) {
		if (firstValue == null) {
			return secondValue;
		} else if (secondValue == null) {
			return firstValue;
		}

		return fromBytes(firstValue)
			.append(fromBytes(secondValue))
			.toBytes();
	}

	public static KeyList fromBytes(byte[] oldValue) {
		var result = new KeyList();
		var buffer = ByteBuffer.wrap(oldValue);
		var size = DataUtils.readVarInt(buffer);

		for (int i = 0; i < size; i++) {
			var len = DataUtils.readVarInt(buffer);
			var bytes = new byte[len];

			buffer.get(bytes);
			result.entries.add(bytes);
		}

		result.entries.sort(UnsignedBytes.lexicographicalComparator());
		return result;
	}

	public byte[] toBytes() {
		var buffer = new WriteBuffer(INITIAL_SIZE);
		buffer.putVarInt(entries.size());
		entries.forEach(entry -> buffer.putVarInt(entry.length).put(entry));

		var result = new byte[buffer.position()];
		buffer.position(0);
		buffer.get(result);
		return result;
	}

	public int size() {
		return entries.size();
	}

	public byte[] key(int index) {
		return entries.get(index);
	}

	private KeyList append(KeyList newList) {
		var result = new KeyList();
		result.entries.addAll(this.entries);
		result.entries.addAll(newList.entries);
		result.entries.sort(UnsignedBytes.lexicographicalComparator());
		return result;
	}
}
