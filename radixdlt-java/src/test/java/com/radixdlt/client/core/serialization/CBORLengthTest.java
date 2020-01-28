/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.core.serialization;

import org.junit.Before;
import org.junit.Test;
import org.radix.utils.primitives.Bytes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

import static org.junit.Assert.assertArrayEquals;

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
