/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.serialization;

import static org.junit.Assert.assertArrayEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.radixdlt.utils.Bytes;
import org.junit.Before;
import org.junit.Test;

public class CBORLengthTest {

	private ObjectMapper cborMapper;

	@Before
	public void setupCborMapper() {
		cborMapper = new ObjectMapper(new CBORFactory());
	}

	@Test
	// Check that length is encoded in a single byte for 23 character strings
	public void testJacksonEncodesCBORLengthsCorrectly() throws JsonProcessingException {
		byte[] expectedData = Bytes.fromHexString("7772616469782e7061727469636c65732e6d657373616765");
		byte[] data = cborMapper.writeValueAsBytes("radix.particles.message");
		assertArrayEquals(expectedData, data);
	}

	@Test
	// Check that the value 23 is encoded in a single byte for integers
	public void testJacksonEncodesCBORIntsCorrectly() throws JsonProcessingException {
		byte[] expectedData = Bytes.fromHexString("17");
		byte[] data = cborMapper.writeValueAsBytes(23);
		assertArrayEquals(expectedData, data);
	}
}
