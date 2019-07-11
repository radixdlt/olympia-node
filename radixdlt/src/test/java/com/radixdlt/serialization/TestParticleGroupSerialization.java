package com.radixdlt.serialization;

import org.junit.BeforeClass;
import org.junit.Test;
import com.radixdlt.atoms.Atom;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.core.ClasspathScanningSerializationPolicy;
import com.radixdlt.serialization.core.ClasspathScanningSerializerIds;

import static org.junit.Assert.assertEquals;

import joptsimple.internal.Strings;

public class TestParticleGroupSerialization {
	static Serialization serialization;

	@BeforeClass
	public static void setupSerializer() {
		serialization = Serialization.create(ClasspathScanningSerializerIds.create(), ClasspathScanningSerializationPolicy.create());
	}

	@Test
	public void testLargeStringSerialization() throws SerializationException {
		long timestamp = 0x0001020304050607L;
		Atom atom = new Atom(timestamp);

		// "massive" must be greater length than (16000 / 4) - 4 = 3996
		String massive = Strings.repeat('X', 4096);
		ParticleGroup pg = ParticleGroup.builder().addMetaData("massive", massive).build();
		atom.addParticleGroup(pg);

		byte[] atombytes = serialization.toDson(atom, Output.HASH);

		assertEquals(4249, atombytes.length);
	}
}
