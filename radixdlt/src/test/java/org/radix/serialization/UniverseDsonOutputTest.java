package org.radix.serialization;

import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializationException;
import com.radixdlt.universe.Universe;
import org.junit.AfterClass;
import org.junit.Test;
import org.radix.GenerateUniverses;
import org.radix.modules.Modules;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.radix.serialization.SerializationTestUtils.compareJson;

/**
 * Serialization for Universe to DSON.
 */
public class UniverseDsonOutputTest extends RadixTest {
	@AfterClass
	public static void afterClass() {
	}

	@Test
	public void roundTripSimpleDson() throws SerializationException {
		DummyTestObject testObject = new DummyTestObject(true);
		byte[] jacksonBytes = getSerialization().toDson(testObject, Output.ALL);
		DummyTestObject jacksonBytesObj = getSerialization().fromDson(jacksonBytes, DummyTestObject.class);
		assertEquals(testObject, jacksonBytesObj);
	}

	@Test
	public void testNONEIsEmpty() throws Exception {
		Universe development = getDevelopmentUniverse();
		byte[] s2Dson = getSerialization().toDson(development, Output.NONE);
		// Note that 0xBF, 0xFF is the CBOR code for a streamed object with 0 properties
		assertArrayEquals(new byte[] { (byte) 0xBF, (byte)0xFF }, s2Dson);
	}

	@Test
	public void testEncodeDecodeALL() throws Exception {
		testEncodeDecode(Output.ALL, false);
	}

	@Test
	public void testEncodeDecodeAPI() throws Exception {
		// Because of the way BasicContainer calculates and caches the hash
		// the hashes will not be the same on deserialization.
		testEncodeDecode(Output.API, false);
	}

//	@Test
//	public void testEncodeDecodeHASH() throws Exception {
//		// Output.HASH does not serialize "serializers" and can't be deserialized
//		testEncodeDecode(Output.HASH);
//	}

//	@Test
//	public void testEncodeDecodeNONE() throws Exception {
//		// Output.NONE does not serialize "serializers" and can't be deserialized
//		testEncodeDecode(Output.NONE);
//	}

	@Test
	public void testEncodeDecodePERSIST() throws Exception {
		testEncodeDecode(Output.PERSIST, false);
	}

	@Test
	public void testEncodeDecodeWIRE() throws Exception {
		testEncodeDecode(Output.WIRE, false);
	}

	private static void testEncodeDecode(Output output, boolean changedHid) throws Exception {
		Universe development = getDevelopmentUniverse();
		byte[] dson1 = getSerialization().toDson(development, output);
		Universe newDevelopment = getSerialization().fromDson(dson1, Universe.class);
		byte[] dson2 = getSerialization().toDson(newDevelopment, output);
		compareJson(toJson(dson1, Universe.class, output), toJson(dson2, Universe.class, output));
	}

	private static String toJson(byte[] dson, Class<?> cls, Output output) throws SerializationException {
		Object o = getSerialization().fromDson(dson, cls);
		return getSerialization().toJson(o, output);
	}

	private static Universe getDevelopmentUniverse() throws Exception {
		GenerateUniverses gu = new GenerateUniverses(getProperties());
		return gu.generateUniverses().stream()
				.filter(Universe::isDevelopment)
				.findAny().get();
	}
}
