package com.radixdlt.serialization;

import org.junit.Before;
import org.junit.Test;
import com.radixdlt.utils.Bytes;

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
