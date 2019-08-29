package org.radix.universe;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.universe.Universe;
import org.radix.atoms.Atom;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.UInt256;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.radix.modules.Modules;
import org.radix.time.TemporalVertex;
import org.radix.validation.ValidationHandler;

import static org.mockito.Mockito.mock;

public class UniverseTest {

	@Before
	public void setUp() {
		Modules.put(Serialization.class, Serialization.getDefault());

		// Atom.getAID currently has an unfortunate dependency on the Constraint machine
		// This will be revisited and cleaned up at a later point but would be too much effort for this change
		final ValidationHandler validationHandler = mock(ValidationHandler.class);
		Modules.put(ValidationHandler.class, validationHandler);
	}

	@After
	public void tearDown() {
		Modules.remove(Serialization.class);
		Modules.remove(ValidationHandler.class);
	}

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testValidUniverse() throws Exception {

        ECKeyPair creator = new ECKeyPair();

        Atom genesisAtom = createGenesisAtom(Universe.computeMagic(creator.getPublicKey(), 0, 0, Universe.UniverseType.TEST, 0), 0);
        genesisAtom.sign(creator);
        genesisAtom.getTemporalProof().add(new TemporalVertex(creator.getPublicKey(), 1, 1, Hash.ZERO_HASH, EUID.ZERO), creator);

        Universe universe = Universe.newBuilder()
        	.port(0)
        	.name("test")
        	.description("test universe")
        	.type(Universe.UniverseType.TEST)
        	.timestamp(0L)
        	.planckPeriod(1L)
        	.creator(creator.getPublicKey())
        	.addAtom(genesisAtom)
        	.build();

        universe.sign(creator);

		UniverseValidator.validate(universe);
    }

    @Test
    public void testUniverseValidationInvalidSignature() throws Exception {
        ECKeyPair creator = new ECKeyPair();

        Atom genesisAtom = createGenesisAtom(Universe.computeMagic(creator.getPublicKey(), 0, 0, Universe.UniverseType.TEST, 0), 0);
        genesisAtom.sign(creator);
        genesisAtom.getTemporalProof().add(new TemporalVertex(creator.getPublicKey(), 1, 1, Hash.ZERO_HASH, EUID.ZERO), creator);

        Universe universe = Universe.newBuilder()
            .port(0)
            .name("test")
            .description("test universe")
            .type(Universe.UniverseType.TEST)
            .timestamp(0L)
            .planckPeriod(1L)
            .creator(creator.getPublicKey())
            .addAtom(genesisAtom)
            .build();

		thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Invalid universe signature");

		UniverseValidator.validate(universe);
    }

    @Test
    public void testUniverseValidationNoTemporalProofs() throws Exception {
        ECKeyPair creator = new ECKeyPair();

        Atom genesisAtom = createGenesisAtom(Universe.computeMagic(creator.getPublicKey(), 0, 0, Universe.UniverseType.TEST, 0), 0);
        genesisAtom.sign(creator);

        Universe universe = Universe.newBuilder()
            .port(0)
            .name("test")
            .description("test universe")
            .type(Universe.UniverseType.TEST)
            .timestamp(0L)
            .planckPeriod(1L)
            .creator(creator.getPublicKey())
            .addAtom(genesisAtom)
            .build();

        universe.sign(creator);

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("All atoms in genesis need to have non-empty temporal proofs");

		UniverseValidator.validate(universe);
    }

    private Atom createGenesisAtom(int magic, long timestamp) throws CryptoException {
        MutableSupplyTokenDefinitionParticle pow = new MutableSupplyTokenDefinitionParticle(
			new RadixAddress((byte) (magic & 0xFF), new ECKeyPair().getPublicKey()),
			"XRD", "Proof of Launch", "Radix Tokens",
			UInt256.ONE,
			null,
			ImmutableMap.of(
				TokenTransition.MINT, TokenPermission.ALL,
				TokenTransition.BURN, TokenPermission.NONE
			)
		);
		Atom genesisAtom = new Atom(timestamp);
		genesisAtom.addParticleGroupWith(pow, Spin.UP);
		return genesisAtom;
    }
}
