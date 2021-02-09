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

import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;

public class SimpleAppendLogTest {
	@Test
	public void appendLogCanBeCreated() throws IOException {
		var path = File.createTempFile("mal-", ".log").getAbsolutePath();
		var appendLog = SimpleAppendLog.open(path);

		checkReadAfterWrite(appendLog, new byte[]{0x01});
		checkReadAfterWrite(appendLog, new byte[]{0x01, 0x02, 0x03, 0x04, 0x05});
		checkReadAfterWrite(appendLog, new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x0C, 0x7F, -1});
	}

	@Test
	public void appendLogCanBeReadFromScratch() throws IOException {
		var path = File.createTempFile("mal-", ".log").getAbsolutePath();
		var appendLog = SimpleAppendLog.open(path);

		appendLog.write(new byte[]{0x01});
		appendLog.write(new byte[]{0x01, 0x02, 0x03, 0x04, 0x05});
		appendLog.write(new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x0C, 0x7F, -1});

		appendLog.close();

		var newAppendLog = SimpleAppendLog.open(path);

		long pos;

		pos = checkSingleChunk(newAppendLog, 0L, new byte[]{0x01});
		pos = checkSingleChunk(newAppendLog, pos, new byte[]{0x01, 0x02, 0x03, 0x04, 0x05});
		pos = checkSingleChunk(newAppendLog, pos, new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x0C, 0x7F, -1});
	}

	private void checkReadAfterWrite(AppendLog appendLog, byte[] data) throws IOException {
		long pos = appendLog.position();
		appendLog.write(data);

		assertArrayEquals(data, appendLog.read(pos));
	}

	private long checkSingleChunk(AppendLog appendLog, long offset, byte[] expect) throws IOException {
		var bytes = appendLog.read(offset);

		assertArrayEquals(expect, bytes);

		return offset + bytes.length + Integer.BYTES;
	}
}