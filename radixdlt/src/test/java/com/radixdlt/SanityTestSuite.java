package com.radixdlt;

import com.google.common.hash.HashCode;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.JSONFormatter;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SanityTestSuite {

	@Test
	public void test_sanity_suite() {

		RRI rri = RRI.from("/JFLKeSQmBZ73YkzWiesdEr2fRT14qCB1DQUvj8KxYQC6m8UTCcF/XRD");
		RRIParticle rriParticle = new RRIParticle(rri, 0L);
		String nonCanonicalJSONString = DefaultSerialization.getInstance().toJson(rriParticle, DsonOutput.Output.ALL);
		String canonicalJSONString = JSONFormatter.sortPrettyPrintJSONString(nonCanonicalJSONString);


		assertEquals("{\n" +
				"    \"destinations\": [\n" +
				"        \":uid:dfd7c486570a7ad40eb948c80cb89376\"\n" +
				"    ],\n" +
				"    \"nonce\": 0,\n" +
				"    \"rri\": \":rri:/JFLKeSQmBZ73YkzWiesdEr2fRT14qCB1DQUvj8KxYQC6m8UTCcF/XRD\",\n" +
				"    \"serializer\": \"radix.particles.rri\",\n" +
				"    \"version\": 100\n" +
				"}", canonicalJSONString);


		Hasher hasher = Sha256Hasher.withDefaultSerialization();

		HashCode hash = hasher.hash(canonicalJSONString);
		String hashHex = hash.toString();

		assertEquals("8a55488122d9565c6693af7dcb73be0c6691e4999efb7e4b38f3c4c6c92a4401", hashHex);
	}
}
