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

package com.radixdlt.store.berkeley.atom;

import com.radixdlt.counters.SystemCounters;
import com.radixdlt.utils.Pair;

import java.io.IOException;
import java.util.function.BiConsumer;

/**
 * Interface for append-only log file. The file consists of variable length chunks with following format:
 * <pre>
 *     [size (64-bit little-endian)] [byte0, byte1, ..., byteN]
 * </pre>
 */
public interface AppendLog {
	/**
	 * Open compressed R/W append log.
	 *
	 * @param path log file path
	 * @param counters system counters to use
	 *
	 * @return append log
	 *
	 * @throws IOException
	 */
	static AppendLog openCompressed(String path, SystemCounters counters) throws IOException {
		return CompressedAppendLog.open(openSimple(path), counters);
	}

	/**
	 * Open plain R/W append log.
	 *
	 * @param path log file path
	 *
	 * @return append log
	 *
	 * @throws IOException
	 */
	static AppendLog openSimple(String path) throws IOException {
		return SimpleAppendLog.open(path);
	}

	/**
	 * Get position at which next chunk will be written.
	 */
	long position();

	/**
	 * Truncate the file to specified length.
	 *
	 * @param position position to which file should be truncated.
	 */
	void truncate(long position);

	/**
	 * Write next chunk.
	 *
	 * @param data data to write
	 *
	 * @return successful result with chunk length or failure with error description.
	 */
	long write(byte[] data) throws IOException;

	/**
	 * Read chunk at specified position.
	 *
	 * @param offset offset to read from
	 *
	 * @return successful result with chunk length or failure with error description.
	 */
	default byte[] read(long offset) throws IOException {
		return readChunk(offset).getFirst();
	}

	/**
	 * Read chunk at specified position.
	 *
	 * @param offset offset to read from
	 *
	 * @return successful result with chunk length or failure with error description.
	 */
	Pair<byte[], Integer> readChunk(long offset) throws IOException;

	/**
	 * Force flushing data to disk.
	 */
	void flush() throws IOException;

	/**
	 * Close append log.
	 */
	void close();

	/**
	 * Scan log from start to end and submit every found chunk and its offset into provided consumer.
	 * Last entry submitted to consumer will have empty array and -1L as a parameters.
	 */
	void forEach(BiConsumer<byte[], Long> chunkConsumer);
}
