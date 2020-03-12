package com.radixdlt.client.core.util;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.serialization.Serialize;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializationException;

public class TestUtils {
	private TestUtils() {
	}

	private static final int HEXDUMP_LINESIZE = 0x20;

	/**
	 * Dump the JSON representation of the binary generated for hashing
	 *
	 * @param atom The atom
	 */
	public static void dumpJsonForHash(Atom atom) {
		try {
			System.out.println(Serialize.getInstance().toJson(atom, DsonOutput.Output.HASH));
		} catch (SerializationException e) {
			throw new IllegalStateException("Failed to serialize", e);
		}
	}

	/**
	 * Dump the DSON representation of the binary generated for hashing
	 *
	 * @param atom The atom
	 */
	public static void dumpDsonForHash(Atom atom) {
		hexdump(atom.toDson());
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
			return (char) b;
		}
		return '.';
	}
}
