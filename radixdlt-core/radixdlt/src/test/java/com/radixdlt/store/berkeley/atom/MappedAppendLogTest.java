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
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static com.radixdlt.store.berkeley.atom.MappedAppendLog.appendOnlyLog;
import static com.radixdlt.store.berkeley.atom.MappedRandomAccessLogReader.randomAccessLog;
import static org.junit.Assert.*;

public class MappedAppendLogTest {
	@Test
	public void appendLogCanBeCreated() throws IOException {
		var path = File.createTempFile("mal-", ".log").getAbsolutePath();

		Result.allOf(appendOnlyLog(path, 10), randomAccessLog(path))
			.onSuccess((appendLog, readLog) -> {
				checkReadAfterWrite(appendLog, readLog, new byte[]{0x01});
				checkReadAfterWrite(appendLog, readLog, new byte[]{0x01, 0x02, 0x03, 0x04, 0x05});
				checkReadAfterWrite(appendLog, readLog, new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x0C, 0x7F, -1});
			});
	}

	private void checkReadAfterWrite(AppendLog appendLog, RandomAccessLogReader readLog, byte[] data) {
		appendLog.write(data)
			.onFailureDo(() -> fail("Write failed"))
			.onSuccessDo(appendLog::flush)
			.flatMap(readLog::read)
			.onFailureDo(() -> fail("Read failed"))
			.onSuccess(read -> assertArrayEquals(data, read));
	}
}