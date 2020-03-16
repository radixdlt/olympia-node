package com.radixdlt.serialization;

import com.radixdlt.TestSetupUtils;
import com.radixdlt.atomos.RRI;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;

import static org.junit.Assert.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * JSON Serialization round trip of {@link RRIParticle} object.
 */
public class RRIParticleSerializationTest extends SerializeObjectEngine<RRIParticle> {
	private static final String NAME = "TEST";
	private static final ECKeyPair keyPair;
	private static final RadixAddress address;
	private static final RRI rri;

	static {
        try {
        	keyPair = new ECKeyPair();
        	address = new RadixAddress((byte) 123, keyPair.getPublicKey());
        	rri = RRI.of(address, NAME);
    	} catch (CryptoException ex) {
    		throw new IllegalStateException("Error while creating RRIParticle", ex);
    	}
	}

    public RRIParticleSerializationTest() {
        super(RRIParticle.class, RRIParticleSerializationTest::get);
    }

    @BeforeClass
    public static void startRRIParticleSerializationTest() {
        TestSetupUtils.installBouncyCastleProvider();
    }

    @Test
    public void testGetters() {
    	RRIParticle p = get();
    	assertEquals(rri, p.getRri());
    	assertEquals(0L, p.getNonce());
    }

    @Test
    public void testToString() {
    	String s = get().toString();
    	assertThat(s, containsString(RRIParticle.class.getSimpleName()));
    	assertThat(s, containsString(rri.toString()));
    }

    private static RRIParticle get() {
    	return new RRIParticle(rri);
    }
}
