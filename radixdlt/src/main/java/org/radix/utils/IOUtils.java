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
