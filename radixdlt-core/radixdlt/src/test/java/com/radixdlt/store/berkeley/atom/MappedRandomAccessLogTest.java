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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.Assert.*;

public class MappedRandomAccessLogTest {
	//Little-endian byte order
	private static final byte[] FILE_CONTENT = {
		// Length                 | Payload
		//------+-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+
		0x01, 0x00, 0x00, 0x00, 0x01,
		0x02, 0x00, 0x00, 0x00, 0x01, 0x02,
		0x03, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03,
		0x04, 0x00, 0x00, 0x00, 0x0A, 0x0B, 0x0C, 0x0D,
	};

	private File testFile;

	@Before
	public void setupFile() throws IOException {
		testFile = File.createTempFile("mral-", ".log");

		try (FileOutputStream fos = new FileOutputStream(testFile)) {
			fos.write(FILE_CONTENT);
		}
	}

	@Test
	public void fileCanBeReadSequentially() {
		MappedRandomAccessLogReader.randomAccessLog(testFile.getAbsolutePath())
			.onFailureDo(Assert::fail)
			.onSuccess(
				log -> log.read(0)
					.onFailureDo(Assert::fail)
					.onSuccess(bytes -> assertArrayEquals(new byte[]{0x01}, bytes))
			)
			.onSuccess(
				log -> log.read(5)
					.onFailureDo(Assert::fail)
					.onSuccess(bytes -> assertArrayEquals(new byte[]{0x01, 0x02}, bytes))
			)
			.onSuccess(
				log -> log.read(11)
					.onFailureDo(Assert::fail)
					.onSuccess(bytes -> assertArrayEquals(new byte[]{0x01, 0x02, 0x03}, bytes))
			)
			.onSuccess(
				log -> log.read(18)
					.onFailureDo(Assert::fail)
					.onSuccess(bytes -> assertArrayEquals(new byte[]{0x0A, 0x0B, 0x0C, 0x0D}, bytes))
			);
	}

	@Test
	public void fileCanBeReadInReverseOrder() {
		MappedRandomAccessLogReader.randomAccessLog(testFile.getAbsolutePath())
			.onFailureDo(Assert::fail)
			.onSuccess(
				log -> log.read(18)
					.onFailureDo(Assert::fail)
					.onSuccess(bytes -> assertArrayEquals(new byte[]{0x0A, 0x0B, 0x0C, 0x0D}, bytes))
			)
			.onSuccess(
				log -> log.read(11)
					.onFailureDo(Assert::fail)
					.onSuccess(bytes -> assertArrayEquals(new byte[]{0x01, 0x02, 0x03}, bytes))
			)
			.onSuccess(
				log -> log.read(5)
					.onFailureDo(Assert::fail)
					.onSuccess(bytes -> assertArrayEquals(new byte[]{0x01, 0x02}, bytes))
			)
			.onSuccess(
				log -> log.read(0)
					.onFailureDo(Assert::fail)
					.onSuccess(bytes -> assertArrayEquals(new byte[]{0x01}, bytes))
			);
	}
}