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

package org.radix.serialization;

import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * Some utilities to help with initialisation of other sub-systems
 * that tests use.
 */
public final class TestSetupUtils {
	private static final int HEXDUMP_LINESIZE = 0x10;

	private TestSetupUtils() {
		throw new IllegalStateException("Can't construct");
	}

	/**
	 * Install the Bouncy Castle crypto provider used for various hashing
	 * and symmetric/asymmetric key functions.
	 */
	public static void installBouncyCastleProvider() {
		Security.insertProviderAt(new BouncyCastleProvider(), 1);
	}

	/**
	 * Useful method for discovering why things went wrong - outputs
	 * a hexdump to {@code System.out}.
	 *
	 * @param bytes bytes to dump
	 */
	public static void hexdump(byte[] bytes) {
		for (int index = 0; index < bytes.length; index += HEXDUMP_LINESIZE) {
			int thisLen = Math.min(HEXDUMP_LINESIZE, bytes.length - index);
			System.out.format("%04X:", index);
			int ofs = 0;
			for (; ofs < thisLen; ++ofs) {
				if (ofs == HEXDUMP_LINESIZE / 2) {
					System.out.format("-%02X", bytes[index + ofs] & 0xFF);
				} else {
					System.out.format(" %02X", bytes[index + ofs] & 0xFF);
				}
			}
			while (ofs < HEXDUMP_LINESIZE) {
				System.out.print("   ");
				ofs += 1;
			}
			System.out.print("  |");
			for (ofs = 0; ofs < thisLen; ++ofs) {
				System.out.print(toPrintable(bytes[index + ofs]));
			}
			while (ofs < HEXDUMP_LINESIZE) {
				System.out.print(' ');
				ofs += 1;
			}
			System.out.println('|');
		}
	}

	private static char toPrintable(byte b) {
		if (b >= 0x20 && b < 0x7F) {
			return (char)b;
		}
		return '.';
	}
}
