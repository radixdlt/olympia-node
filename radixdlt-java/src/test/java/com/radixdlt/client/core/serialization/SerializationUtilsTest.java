package com.radixdlt.client.core.serialization;

import java.nio.ByteBuffer;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;

/**
 * Tests for utility methods in {@link SerializationUtils}.
 */
public class SerializationUtilsTest {

	/**
	 * Here we test all the size boundaries to make sure they
	 * are as expected.
	 */
	@Test
	public void testIntLength() {
		// Test boundary cases for each encoding length.
		assertEquals(1, SerializationUtils.intLength(0));
		assertEquals(1, SerializationUtils.intLength(159));
		assertEquals(2, SerializationUtils.intLength(160));
		assertEquals(2, SerializationUtils.intLength(8191));
		assertEquals(3, SerializationUtils.intLength(8192));
		assertEquals(4, SerializationUtils.intLength(2097152));
		assertEquals(4, SerializationUtils.intLength(SerializationUtils.SERIALIZE_MAX_INT));

		assertThatThrownBy(() -> SerializationUtils.intLength(-1))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> SerializationUtils.intLength(SerializationUtils.SERIALIZE_MAX_INT + 1))
			.isInstanceOf(IllegalArgumentException.class);
	}

	/**
	 * Here we test every fourth serializable integer
	 * to make sure that we can complete a round trip.
	 */
	@Test
	public void testIntEncodeDecode() {
		ByteBuffer bytes = ByteBuffer.allocate(0x10);
		for (int i = 0; i < SerializationUtils.SERIALIZE_MAX_INT; i += 4) {
			int intLength = SerializationUtils.intLength(i);
			bytes.clear();
			SerializationUtils.encodeInt(i, bytes);
			assertEquals(intLength, bytes.position());
			bytes.flip();
			assertEquals(i, SerializationUtils.decodeInt(bytes));
		}
	}

}
