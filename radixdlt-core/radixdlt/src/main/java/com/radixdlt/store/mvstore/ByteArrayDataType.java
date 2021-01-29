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
import org.h2.mvstore.type.DataType;

import java.nio.ByteBuffer;

public class ByteArrayDataType implements DataType {
	private byte[] cast(Object a) {
		return (byte[]) a;
	}

	@Override
	public int compare(Object a, Object b) {
		return UnsignedBytes.lexicographicalComparator().compare(cast(a), cast(b));
	}

	@Override
	public int getMemory(Object obj) {
		return cast(obj).length;
	}

	@Override
	public void write(WriteBuffer buff, Object obj) {
		byte[] bytes = cast(obj);
		buff.putVarInt(bytes.length);
		buff.put(bytes);
	}

	@Override
	public void write(WriteBuffer buff, Object[] obj, int len, boolean key) {
		for (int i = 0; i < len; i++) {
			write(buff, obj[i]);
		}
	}

	@Override
	public Object read(ByteBuffer buff) {
		int len = DataUtils.readVarInt(buff);
		var bytes = new byte[len];
		buff.get(bytes);

		return bytes;
	}

	@Override
	public void read(ByteBuffer buff, Object[] obj, int len, boolean key) {
		for (int i = 0; i < len; i++) {
			obj[i] = read(buff);
		}
	}
}
