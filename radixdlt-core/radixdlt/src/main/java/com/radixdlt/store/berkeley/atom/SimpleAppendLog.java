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
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

import static java.nio.ByteBuffer.allocate;

/**
 * Implementation of simple append-only log
 */
public class SimpleAppendLog implements AppendLog {
	private final FileChannel channel;
	private final ByteBuffer sizeBufferW;
	private final ByteBuffer sizeBufferR;

	private SimpleAppendLog(final FileChannel channel) {
		this.channel = channel;
		this.sizeBufferW = allocate(Integer.BYTES).order(ByteOrder.BIG_ENDIAN);
		this.sizeBufferR = allocate(Integer.BYTES).order(ByteOrder.BIG_ENDIAN);
	}

	public static AppendLog openCompressed(String path, SystemCounters counters) throws IOException {
		return new CompressedAppendLog(open(path), counters);
	}

	public static AppendLog open(String path) throws IOException {
		@SuppressWarnings("resource")
		var channel = new RandomAccessFile(path, "rw").getChannel();
		channel.position(channel.size());
		return new SimpleAppendLog(channel);
	}

	@Override
	public long write(byte[] data) throws IOException {
		synchronized (channel) {
			var position = channel.position();

			sizeBufferW.clear().putInt(data.length).clear();
			checkedWrite(Integer.BYTES, sizeBufferW);
			checkedWrite(data.length, ByteBuffer.wrap(data));

			return position;
		}
	}

	@Override
	public Pair<byte[], Integer> readChunk(long offset) throws IOException {
		synchronized (channel) {
			checkedRead(offset, sizeBufferR.clear());
			var readLength = sizeBufferR.clear().getInt();
			return Pair.of(checkedRead(offset + Integer.BYTES, allocate(readLength)).array(), readLength);
		}
	}

	@Override
	public void flush() throws IOException {
		synchronized (channel) {
			channel.force(true);
		}
	}

	@Override
	public long position() {
		try {
			synchronized (channel) {
				return channel.position();
			}
		} catch (IOException e) {
			throw new IllegalStateException("Unable to obtain current position in log", e);
		}
	}

	@Override
	public void truncate(long position) {
		try {
			synchronized (channel) {
				channel.truncate(position);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Unable to truncate log", e);
		}
	}

	@Override
	public void close() {
		try {
			synchronized (channel) {
				channel.close();
			}
		} catch (IOException e) {
			throw new RuntimeException("Error while closing log", e);
		}
	}

	private void checkedWrite(int length, ByteBuffer buffer) throws IOException {
		int len = channel.write(buffer);

		if (len != length) {
			throw new IOException("Written less bytes than requested: " + len + " vs " + length);
		}
	}

	private ByteBuffer checkedRead(long offset, ByteBuffer buffer) throws IOException {
		int len = channel.read(buffer.clear(), offset);

		if (len != buffer.capacity()) {
			// Force flush and try again
			channel.force(true);
			len = channel.read(buffer.clear(), offset);
		}

		if (len != buffer.capacity()) {
			throw new IOException("Got less bytes than requested: " + len + " vs " + buffer.capacity()
									  + " at " + offset + ", size " + channel.size());
		}
		return buffer;
	}
}
