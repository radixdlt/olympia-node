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
import com.radixdlt.utils.functional.ResultMappers.Mapper2;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import static com.radixdlt.store.berkeley.atom.MemoryMappingUtil.mmap;
import static com.radixdlt.store.berkeley.atom.MemoryMappingUtil.remap;
import static com.radixdlt.store.berkeley.atom.MemoryMappingUtil.unmap;
import static com.radixdlt.utils.functional.Failure.failure;
import static com.radixdlt.utils.functional.Result.fail;
import static com.radixdlt.utils.functional.Result.ok;
import static com.radixdlt.utils.functional.Tuple.tuple;
import static java.nio.channels.FileChannel.MapMode;

public class MappedFile {
	public static final long DEFAULT_REGION_SIZE = 10 * 1024 * 1024;

	private final FileChannel channel;
	private final long regionSize;

	private MappedByteBuffer buffer;
	private long regionOffset;

	public MappedFile(FileChannel channel, MappedByteBuffer buffer, long regionOffset, long regionSize) {
		this.channel = channel;
		this.buffer = buffer;
		this.regionOffset = regionOffset;
		this.regionSize = regionSize;
	}

	protected void flushBuf() {
		buffer.force();
	}

	protected long currentPosition() {
		return regionOffset + buffer.position();
	}

	protected Result<Long> truncateAt(long position) {
		try {
			final var length = regionOffset + (long) buffer.position();
			unmap(buffer);
			channel.truncate(length);
			return mapAt(length, regionSize, MapMode.READ_WRITE);
		} catch (IOException e) {
			return fail(Failure.failure("Error while truncating file", e));
		}
	}

	protected Result<byte[]> readFrom(long offset) {
		long limit = regionOffset + buffer.limit() - Long.BYTES;
		if (offset < regionOffset || offset >= limit) {
			remapAt(offset);
		}

		int len = buffer.getInt();

		if (len > regionSize) {
			return fail(failure("Requested chunk is too big ("
									+ len
									+ " bytes) to fit to memory with current configuration of "
									+ getClass()
									+ "(" + regionSize + " bytes)"));
		}

		if (buffer.remaining() < len) {
			var result = remapAt(regionOffset + buffer.position());

			if (result.isFailure()) {
				return result.map(__ -> new byte[0]);
			}
		}

		var output = new byte[len];
		buffer.get(output);

		return ok(output);
	}

	private Result<Long> remapAt(long offset) {
		unmap(buffer);
		return fileSize(channel)
			.flatMap((channel, size) -> mapAt(offset, calculateRegionSize(offset, size), MapMode.READ_ONLY));
	}

	private long calculateRegionSize(long offset, Long size) {
		return ((offset + regionSize) <= size)
			   ? regionSize
			   : size - offset;
	}

	private Result<Long> mapAt(long offset, long size, MapMode mapMode) {
		return mmap(channel, offset, size, mapMode)
			.onSuccess(buf -> buffer = buf)
			.map(__ -> offset)
			.onSuccess(len -> regionOffset = len);
	}

	protected Result<Long> write(final byte[] bytes, int offset, int length) {
		var initialPos = currentPosition();
		while (length > buffer.remaining()) {
			final int chunk = buffer.remaining();
			buffer.put(bytes, offset, chunk);
			offset += chunk;
			length -= chunk;

			var result = remap(buffer, channel, offset, regionSize)
				.map(tuple -> tuple.map((buf, off) -> {
					buffer = buf;
					regionOffset = off;
					return 0L;
				}));

			if (result.isFailure()) {
				return result;
			}
		}

		buffer.put(bytes, offset, length);
		return ok(initialPos);
	}

	protected boolean closeFile() {
		try {
			final var length = regionOffset + (long) buffer.position();
			unmap(buffer);
			channel.truncate(length);
			channel.close();
			return true;
		} catch (final IOException ex) {
			return false;
		}
	}

	protected static Mapper2<FileChannel, Long> fileSize(FileChannel channel) {
		return () -> {
			try {
				return ok(tuple(channel, channel.size()));
			} catch (IOException e) {
				return fail(failure("Unable to obtain file length"));
			}
		};
	}
}
