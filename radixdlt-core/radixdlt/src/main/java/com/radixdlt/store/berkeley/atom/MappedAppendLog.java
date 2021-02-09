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

import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import static com.radixdlt.store.berkeley.atom.MemoryMappingUtil.mmap;
import static com.radixdlt.store.berkeley.atom.MemoryMappingUtil.open;
import static com.radixdlt.utils.functional.Tuple.tuple;
import static java.nio.channels.FileChannel.MapMode;

public class MappedAppendLog extends MappedFile implements AppendLog {
	public static final long DEFAULT_REGION_SIZE = 10 * 1024 * 1024;

	private MappedAppendLog(final FileChannel channel, MappedByteBuffer buffer, long regionOffset, long regionSize) {
		super(channel, buffer, regionOffset, regionSize);
	}

	public static Result<AppendLog> appendOnlyLog(String path) {
		return appendOnlyLog(path, DEFAULT_REGION_SIZE);
	}

	public static Result<AppendLog> appendOnlyLog(String path, long regionSize) {
		return open(path, "rw")
			.chain2(MappedFile::fileSize)
			.chain3((channel, size) -> mmap(channel, size, regionSize, MapMode.READ_WRITE).map(buffer -> tuple(channel, buffer, size)))
			.map((channel, buf, size) -> new MappedAppendLog(channel, buf, size, regionSize));
	}

	@Override
	public Result<Long> write(byte[] data) {
		ByteBuffer buf = ByteBuffer.allocate(Integer.BYTES);
		buf.putInt(data.length);

		return write(buf.array(), 0, buf.position()).flatMap(__ -> write(data, 0, data.length));
	}

	@Override
	public synchronized void flush() {
		flushBuf();
	}

	@Override
	public long position() {
		return currentPosition();
	}

	@Override
	public Result<Long> truncate(long position) {
		return truncateAt(position);
	}

	@Override
	public synchronized boolean close() {
		return closeFile();
	}
}
