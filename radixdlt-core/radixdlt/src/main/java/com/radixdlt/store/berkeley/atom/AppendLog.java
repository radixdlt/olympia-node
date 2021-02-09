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

import com.radixdlt.utils.functional.Result;

/**
 * Interface for append-only log file. The file consists of variable length chunks with following format:
 * <pre>
 *     [size (64-bit little-endian)] [byte0, byte1, ..., byteN]
 * </pre>
 */
public interface AppendLog {
	/**
	 * Get position at which next chunk will be written.
	 */
	long position();

	/**
	 * Truncate the file to specified length.
	 *
	 * @param position position to which file should be truncated.
	 *
	 * @return successful result with new file length or failure with error description.
	 */
	Result<Long> truncate(long position);

	/**
	 * Write next chunk.
	 *
	 * @param data data to write
	 *
	 * @return successful result with chunk length or failure with error description.
	 */
	Result<Long> write(byte[] data);

	/**
	 * Force flushing data to disk.
	 */
	void flush();

	/**
	 * Close append log.
	 */
	boolean close();
}
