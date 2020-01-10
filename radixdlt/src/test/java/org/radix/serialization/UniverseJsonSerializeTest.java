package org.radix.serialization;

import org.radix.GenerateUniverses;
import com.radixdlt.universe.Universe;

/**
 * Serialization for Universe to JSON.
 */
public class UniverseJsonSerializeTest extends SerializeObject<Universe> {

	public UniverseJsonSerializeTest() {
		super(Universe.class, UniverseJsonSerializeTest::getDev);
	}

	private static Universe getDev() {
		try {
			GenerateUniverses gu = new GenerateUniverses(getProperties());
			return gu.generateUniverses().stream()
					.filter(Universe::isDevelopment)
					.findAny().get();
		} catch (Exception e) {
			throw new IllegalStateException("Can't create universes", e);
		}
	}
}
