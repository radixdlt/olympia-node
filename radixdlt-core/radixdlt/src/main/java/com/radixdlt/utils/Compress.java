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

package com.radixdlt.utils;

import org.xerial.snappy.Snappy;
import org.xerial.snappy.SnappyFramedInputStream;
import org.xerial.snappy.SnappyFramedOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Common utility methods for compression/decompression.
 */
public class Compress {
	private Compress() {
		throw new UnsupportedOperationException("Utility class should not be instantiated.");
	}

	/**
	 * Compresses input byte array into output byte array.
	 *
	 * @param input source data to compress
	 * @return compressed output.
	 *
	 * @throws IOException
	 */
	public static byte[] compress(byte[] input) throws IOException {
		var out = new ByteArrayOutputStream();
		var os = new SnappyFramedOutputStream(out);
		os.transferFrom(new ByteArrayInputStream(input));
		os.close();
		return out.toByteArray();
	}

	/**
	 * Decompresses input byte array into output byte array.
	 *
	 * @param input source data to decompress
	 * @return decompressed output.
	 *
	 * @throws IOException
	 */
	public static byte[] uncompress(byte[] input) throws IOException {
		var is = new SnappyFramedInputStream(new ByteArrayInputStream(input));
		var os = new ByteArrayOutputStream(Snappy.uncompressedLength(input));
		is.transferTo(os);
		os.close();
		return os.toByteArray();
	}
}
