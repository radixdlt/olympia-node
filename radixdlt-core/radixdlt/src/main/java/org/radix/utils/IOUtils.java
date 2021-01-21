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

package org.radix.utils;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

import com.google.common.io.CharStreams;
import com.radixdlt.utils.RadixConstants;

import org.apache.logging.log4j.Logger;

/**
 * Some utility methods dealing with streams and closeables.
 */
public final class IOUtils {
	private IOUtils() {
		throw new IllegalStateException("Can't construct");
	}

	/**
	 * Reads the specified input stream as a {@code String} using the default
	 * character set specified in {@link RadixConstants#STANDARD_CHARSET}.
	 * The input stream is closed before this method completes.
	 *
	 * @param inputStream The input stream to read from
	 * @return the contents of the input stream as a {@code String}
	 * @throws IOException If an I/O error occurs
	 */
	public static String toString(InputStream inputStream) throws IOException {
		return toString(inputStream, RadixConstants.STANDARD_CHARSET);
	}

	/**
	 * Reads the specified input stream as a {@code String}, using the
	 * specified character set.
	 * The input stream is closed before this method completes.
	 *
	 * @param inputStream The input stream to read from
	 * @param charset The character set to use for reading
	 * @return the contents of the input stream as a {@code String}
	 * @throws IOException If an I/O error occurs
	 */
	public static String toString(InputStream inputStream, Charset charset) throws IOException {
		try (Reader reader = new InputStreamReader(inputStream, charset)) {
			return CharStreams.toString(reader);
		}
	}

	/**
	 * Closes the specified {@link Closeable} suppressing any
	 * {@link IOException} thrown.
	 *
	 * @param c The {@link Closeable} to close
	 */
	public static void closeSafely(Closeable c) {
		closeSafely(c, null);
	}

	/**
	 * Closes the specified {@link Closeable} suppressing any
	 * {@link IOException} and logging to the specified {@link Logger}.
	 * Log messages are suppressed if {@code log} is {@code null}.
	 *
	 * @param c The {@link Closeable} to close
	 * @param log The {@link Logger} to output exception information on,
	 * 		or {@code null} if no exception logs are required
	 */
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
