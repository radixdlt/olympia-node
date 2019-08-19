package org.radix.network2;

/**
 * FIXME: This is temporary until things are more stable.
 * <p>
 * There is a similar method in the RadixText class, but this
 * is unavailable at runtime, as it's in the test classes.
 */
public class DebugUtils {
	private DebugUtils() {
	}

	private static final int HEXDUMP_LINESIZE = 0x10;

	/**
	 * Useful method for discovering why things went wrong - outputs
	 * a hexdump to a string.
	 *
	 * @param bytes bytes to dump
	 */
	public static String hexdump(byte[] bytes) {
		StringBuffer sb = new StringBuffer();
		for (int index = 0; index < bytes.length; index += HEXDUMP_LINESIZE) {
			int thisLen = Math.min(HEXDUMP_LINESIZE, bytes.length - index);
			sb.append(String.format("%04X:", index));
			int ofs = 0;
			for (; ofs < thisLen; ++ofs) {
				if (ofs == HEXDUMP_LINESIZE / 2) {
					sb.append(String.format("-%02X", bytes[index + ofs] & 0xFF));
				} else {
					sb.append(String.format(" %02X", bytes[index + ofs] & 0xFF));
				}
			}
			while (ofs < HEXDUMP_LINESIZE) {
				sb.append("   ");
				ofs += 1;
			}
			sb.append("  |");
			for (ofs = 0; ofs < thisLen; ++ofs) {
				sb.append(toPrintable(bytes[index + ofs]));
			}
			while (ofs < HEXDUMP_LINESIZE) {
				sb.append(' ');
				ofs += 1;
			}
			sb.append('|');
			sb.append(System.lineSeparator());
		}
		return sb.toString();
	}

	private static char toPrintable(byte b) {
		if (b >= 0x20 && b < 0x7F) {
			return (char) b;
		}
		return '.';
	}
}

