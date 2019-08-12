package org.radix.utils;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.radix.logging.Logger;

public final class IOUtils {

	private IOUtils() {
		throw new IllegalStateException("Can't construct");
	}

	public static String toString(InputStream inputStream) throws IOException {
		return toString(inputStream, Charset.defaultCharset());
	}

	public static String toString(InputStream inputStream, Charset charset) throws IOException {
		StringBuilder builder = new StringBuilder();

		byte[] bytes = new byte[8192];
		int read = -1;

		while ((read = inputStream.read(bytes)) != -1) {
			builder.append(new String(bytes, 0, read, charset));
		}

		return builder.toString();
	}

	public static void closeSafely(Closeable c) {
		closeSafely(c, null);
	}

	public static void closeSafely(Closeable c, Logger log) {
		try {
			c.close();
		} catch (IOException e) {
			if (log != null) {
				log.error("While closing", e);
			}
		}
	}

}
