package org.radix.integration.universe;

import com.radixdlt.common.Atom;
import org.junit.Test;
import org.radix.integration.RadixTest;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.Bytes;
import org.radix.universe.UniverseValidator;

public class UniverseValidationTest extends RadixTest {

    @Test
    public void testLoadingUniverse() throws Exception {
        byte[] bytes = Bytes.fromBase64String(getProperties().get("universe"));
        Universe universe = getSerialization().fromDson(bytes, Universe.class);
        UniverseValidator.validate(universe);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testLoadingUniverseHasImmutableGenesis() throws Exception {
        byte[] bytes = Bytes.fromBase64String(getProperties().get("universe"));
        Universe universe = getSerialization().fromDson(bytes, Universe.class);
        universe.getGenesis().add(new Atom());
    }
}
