/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.utils;

public final class Shorts {
	private Shorts() {
		throw new IllegalStateException("Can't construct");
	}

	public static byte[] toByteArray(short value) {
		return new byte[] {
			(byte) (value >> 8),
			(byte) value
		};
	}

	public static short fromByteArray(byte[] bytes) {
		if (bytes == null || bytes.length == 0) {
			throw new IllegalArgumentException("Array is null or has zero length for 'int' conversion");
		}

		int length = Math.min(bytes.length, Short.BYTES);
		short value = 0;

		for (int b = bytes.length - length; b < bytes.length; b++) {
			value |= (bytes[b] & 0xFFL);

			if (b < bytes.length - 1) {
				value = (short) (value << 8);
			}
		}

		return value;
	}
}
