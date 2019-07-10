package org.radix.integration.universe;

import org.junit.Test;
import org.radix.atoms.Atom;
import org.radix.integration.RadixTest;
import org.radix.modules.Modules;
import org.radix.properties.RuntimeProperties;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.Bytes;

public class UniverseValidationTest extends RadixTest {

    @Test
    public void testLoadingUniverse() throws Exception {
        byte[] bytes = Bytes.fromBase64String(Modules.get(RuntimeProperties.class).get("universe"));
        Universe universe = Modules.get(Serialization.class).fromDson(bytes, Universe.class);
        universe.validate();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testLoadingUniverseHasImmutableGenesis() throws Exception {
        byte[] bytes = Bytes.fromBase64String(Modules.get(RuntimeProperties.class).get("universe"));
        Universe universe = Modules.get(Serialization.class).fromDson(bytes, Universe.class);
        universe.getGenesis().add(new Atom());
    }
}
