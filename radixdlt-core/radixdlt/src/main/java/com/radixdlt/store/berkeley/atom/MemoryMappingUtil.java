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
import com.radixdlt.utils.functional.Tuple.Tuple2;

import java.io.FileNotFoundException;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static com.radixdlt.utils.functional.Failure.failure;
import static com.radixdlt.utils.functional.Result.fail;
import static com.radixdlt.utils.functional.Result.ok;
import static com.radixdlt.utils.functional.Tuple.tuple;
import static java.nio.channels.FileChannel.MapMode;

/**
 * Helper routines for managing memory mapped files.
 */
class MemoryMappingUtil {
	private MemoryMappingUtil() {
		// private constructor for utility class
	}

	private static final int NUM_RETRIES = 10;
	private static Method CLEANER_METHOD = null;
	private static Method CLEAN_METHOD = null;
	private static final AtomicBoolean SETUP_DONE = new AtomicBoolean(false);

	private static void initUnsafe(final MappedByteBuffer mappedByteBuffer) {
		if (CLEANER_METHOD != null) {
			return;
		}

		try {
			AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
				CLEANER_METHOD = mappedByteBuffer.getClass().getMethod("cleaner");
				CLEANER_METHOD.setAccessible(true);
				CLEAN_METHOD = CLEANER_METHOD.invoke(mappedByteBuffer).getClass().getMethod("clean");
				return null;
			});
		} catch (PrivilegedActionException e) {
			throw new IllegalStateException("Unable to initialize memory mapping file API");
		}
	}

	public static void unmap(final MappedByteBuffer buffer) {
		try {
			CLEAN_METHOD.invoke(CLEANER_METHOD.invoke(buffer));
		} catch (IllegalAccessException | InvocationTargetException e) {
			//throw new IllegalStateException("Unable to invoke cleaner");
		}
	}

	public static Result<MappedByteBuffer> mmap(
		final FileChannel channel,
		final long start,
		final long size,
		MapMode mapMode
	) {
		var retry = NUM_RETRIES;

		while (true) {
			retry--;

			try {
				final MappedByteBuffer map = channel.map(mapMode, start, size);
				map.order(ByteOrder.LITTLE_ENDIAN);

				if (SETUP_DONE.compareAndSet(false, true)) {
					initUnsafe(map);
				}

				return ok(map);
			} catch (final IOException e) {
				if (retry > 0) {
					Thread.yield();
					continue;
				}

				return fail(failure(e.getMessage(), e));
			}
		}
	}

	public static Result<Tuple2<MappedByteBuffer, Long>> remap(MappedByteBuffer buffer, FileChannel channel, long offset, long length) {
		try {
			unmap(buffer);
			final long newOffset = offset + buffer.position();
			channel.truncate(channel.size() + length);

			return mmap(channel, newOffset, length, MapMode.READ_WRITE)
				.map(buf -> tuple(buf, newOffset));
		} catch (final Exception ex) {
			return Result.fail(Failure.failure("Unable to remap", ex));
		}
	}

	public static Result<FileChannel> open(String path, String mode) {
		try {
			return ok(new RandomAccessFile(path, mode).getChannel());
		} catch (FileNotFoundException e) {
			return fail(failure("Unable to open file " + path));
		}
	}
}
