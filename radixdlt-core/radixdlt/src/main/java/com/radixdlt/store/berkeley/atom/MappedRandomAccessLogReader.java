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

import com.radixdlt.utils.functional.Failure;
import com.radixdlt.utils.functional.Result;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

import static com.radixdlt.store.berkeley.atom.MemoryMappingUtil.open;
import static com.radixdlt.utils.functional.Failure.failure;
import static com.radixdlt.utils.functional.Result.fail;
import static com.radixdlt.utils.functional.Result.ok;

/**
 * Implementation of the random access log reader
 */
public class MappedRandomAccessLogReader implements RandomAccessLogReader {
	private final FileChannel channel;

	private MappedRandomAccessLogReader(final FileChannel channel) {
		this.channel = channel;
	}

	public static Result<RandomAccessLogReader> randomAccessLog(String path) {
		return open(path, "r").map(MappedRandomAccessLogReader::new);
	}

	@Override
	public Result<byte[]> read(long offset) {
		try {
			ByteBuffer chunkSizeBytes = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
			channel.position(offset).read(chunkSizeBytes);
			int len = chunkSizeBytes.clear().getInt();
			ByteBuffer chunkBytes = ByteBuffer.allocate(len);
			channel.read(chunkBytes);

			return ok(chunkBytes.array());
		} catch (IOException e) {
			return fail("Error reading log", e);
		}
	}

	@Override
	public synchronized void close() {
		try {
			channel.close();
		} catch (IOException e) {
			//Ignore errors here
		}
	}
}
