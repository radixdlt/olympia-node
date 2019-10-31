package org.radix.serialization;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
		try {
            Class<?> jceSecurity = Class.forName("javax.crypto.JceSecurity");
            Field isRestricted = jceSecurity.getDeclaredField("isRestricted");
            isRestricted.setAccessible(true);
            if (Boolean.TRUE.equals(isRestricted.get(null))) {
                if (Modifier.isFinal(isRestricted.getModifiers())) {
                    Field modifiers = Field.class.getDeclaredField("modifiers");
                    modifiers.setAccessible(true);
                    modifiers.setInt(isRestricted, isRestricted.getModifiers() & ~Modifier.FINAL);
                }
                isRestricted.setBoolean(null, false);
            }
            isRestricted.setAccessible(false);
			Security.insertProviderAt(new BouncyCastleProvider(), 1);
		} catch (ReflectiveOperationException | SecurityException ex) {
			throw new IllegalStateException("Can't install Bouncy Castle security provider", ex);
		}
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
