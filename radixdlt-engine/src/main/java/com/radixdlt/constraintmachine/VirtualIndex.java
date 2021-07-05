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

import com.radixdlt.utils.Bytes;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class VirtualIndex {
	private final byte[] virtualBytes;
	private final int virtualStart;
	private final int virtualLength;

	public VirtualIndex(byte[] virtualBytes) {
		this.virtualBytes = virtualBytes;
		this.virtualStart = 0;
		this.virtualLength = 0;
	}

	public VirtualIndex(byte[] virtualBytes, int virtualStart, int virtualLength) {
		this.virtualBytes = virtualBytes;
		this.virtualStart = virtualStart;
		this.virtualLength = virtualLength;
	}

	public boolean test(ByteBuffer buf) {
		var start = buf.position();
		var end = buf.limit();
		var substateBytes = new byte[end - start];
		buf.get(substateBytes);
		if (substateBytes.length != virtualBytes.length + virtualLength) {
			return false;
		}

		if (!Arrays.equals(virtualBytes, 0, virtualStart, substateBytes, 0, virtualStart)) {
			return false;
		}

		if (!Arrays.equals(virtualBytes, virtualStart, virtualBytes.length, substateBytes, virtualStart + virtualLength, substateBytes.length)) {
			return false;
		}

		return true;
	}

	@Override
	public String toString() {
		return String.format("%s{bytes=%s virtualStart=%s virtualLength=%s}",
			this.getClass().getSimpleName(),
			Bytes.toHexString(virtualBytes),
			virtualStart,
			virtualLength
		);
	}
}
