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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.radixdlt.counters.SystemCounters;
import com.radixdlt.utils.Compress;
import com.radixdlt.utils.Pair;

import java.io.IOException;

import static com.radixdlt.counters.SystemCounters.CounterType.PERSISTENCE_ATOM_LOG_WRITE_BYTES;
import static com.radixdlt.counters.SystemCounters.CounterType.PERSISTENCE_ATOM_LOG_WRITE_COMPRESSED;

public class CompressedAppendLog implements AppendLog {
	private static final Logger log = LogManager.getLogger();

	private final AppendLog delegate;
	private final SystemCounters counters;

	public CompressedAppendLog(final AppendLog delegate, final SystemCounters counters) {
		this.delegate = delegate;
		this.counters = counters;
	}

	@Override
	public long position() {
		return delegate.position();
	}

	@Override
	public void truncate(final long position) {
		delegate.truncate(position);
	}

	@Override
	public void write(final byte[] data) throws IOException {
		byte[] compressedData = Compress.compress(data);

		counters.add(PERSISTENCE_ATOM_LOG_WRITE_BYTES, data.length);
		counters.add(PERSISTENCE_ATOM_LOG_WRITE_COMPRESSED, compressedData.length);

		delegate.write(compressedData);
	}

	@Override
	public Pair<byte[], Integer> readChunk(final long offset) throws IOException {
		var result = delegate.readChunk(offset);
		return Pair.of(Compress.uncompress(result.getFirst()), result.getSecond());
	}

	@Override
	public void flush() throws IOException {
		delegate.flush();
	}

	@Override
	public void close() {
		delegate.close();
	}

}
