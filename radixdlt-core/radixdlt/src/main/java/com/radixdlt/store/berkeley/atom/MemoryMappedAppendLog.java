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
import java.io.RandomAccessFile;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

public class MemoryMappedAppendLog implements AppendLog, ReadOnlyLog {
	private static final int NUM_RETRIES = 10;

	private MappedByteBuffer mappedBuffer;
	private long mappingOffset;
	private RandomAccessFile randomAccessFile;
	private int regionLength;

	private static Method cleanerMethod = null;
	private static Method cleanMethod = null;

	private void initUnsafe(final MappedByteBuffer mbb) {
		if (cleanerMethod != null) {
			return;
		}

		try {
			AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
				cleanerMethod = mbb.getClass().getMethod("cleaner");
				cleanerMethod.setAccessible(true);
				cleanMethod = cleanerMethod.invoke(mbb).getClass().getMethod("clean");
				return null;
			});
		} catch (PrivilegedActionException e) {
			throw new IllegalStateException("Unable to initialize memory mapping file API");
		}
	}

	@Override
	public long position() {
		return this.mappingOffset + mappedBuffer.position();
	}

	@Override
	public Result<Long> truncate(long position) {
		//unmap, setLength, map
		return null;
	}

	@Override
	public Result<Integer> write(byte[] data) {
		write(data, 0, data.length);
		return Result.ok(data.length);
	}

	private synchronized void write(final byte[] bytes, int offset, int length) {
		while (length > mappedBuffer.remaining()) {
			final int chunk = mappedBuffer.remaining();
			mappedBuffer.put(bytes, offset, chunk);
			offset += chunk;
			length -= chunk;
			remap(mappedBuffer);
		}
		mappedBuffer.put(bytes, offset, length);
	}

	@Override
	public Result<byte[]> read(long offset) {
		return null;
	}


	private synchronized Result<MappedByteBuffer> remap(MappedByteBuffer mappedBuffer) {
		try {
			cleanup(mappedBuffer);
			final long offset = this.mappingOffset + mappedBuffer.position();
			final int length = mappedBuffer.remaining() + regionLength;
			final long fileLength = randomAccessFile.length() + regionLength;
			randomAccessFile.setLength(fileLength);

			var newBuffer = initMapping(randomAccessFile.getChannel(), offset, length);
			mappingOffset = offset;
			return Result.ok(newBuffer);
		} catch (final Exception ex) {
			return Result.fail(Failure.failure("Unable to remap", ex));
		}
	}

	@Override
	public synchronized void flush() {
		mappedBuffer.force();
	}

	@Override
	public synchronized boolean close() {
		try {
			final long position = mappedBuffer.position();
			final long length = mappingOffset + position;
			cleanup(mappedBuffer);
			randomAccessFile.setLength(length);
			randomAccessFile.close();
			return true;
		} catch (final IOException ex) {
			return false;
		}
	}

	private static MappedByteBuffer initMapping(final FileChannel fileChannel, final long start, final int size) throws IOException {
		var retry = NUM_RETRIES;

		while (true) {
			retry--;

			try {
				final MappedByteBuffer map = fileChannel.map(FileChannel.MapMode.READ_WRITE, start, size);
				map.order(ByteOrder.LITTLE_ENDIAN);
				return map;
			} catch (final IOException e) {
				if (retry > 0) {
					Thread.yield();
					continue;
				}

				throw e;
			}
		}
	}

	private static void cleanup(final MappedByteBuffer buffer) {
		try {
			cleanMethod.invoke(cleanerMethod.invoke(buffer));
		} catch (IllegalAccessException | InvocationTargetException e) {
			throw new IllegalStateException("Unable to invoke cleaner");
		}
	}

}
