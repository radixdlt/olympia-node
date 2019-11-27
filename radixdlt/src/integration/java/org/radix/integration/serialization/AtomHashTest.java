package org.radix.integration.serialization;

import com.radixdlt.common.Atom;
import com.radixdlt.universe.Universe;
import org.junit.Test;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.atomos.RRI;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.CryptoException;
import org.radix.integration.RadixTest;
import org.radix.modules.Modules;

import static org.assertj.core.api.Assertions.assertThat;

public class AtomHashTest extends RadixTest {
	@Test
	public void testThatParticleSpinAffectsAtomHash() throws CryptoException {
		Universe universe = Modules.get(Universe.class);
		RRIParticle p = new RRIParticle(RRI.of(RadixAddress.from(universe, new ECKeyPair().getPublicKey()), "test"));
		Atom atom1 = new Atom();
		atom1.addParticleGroupWith(p, Spin.UP);

		Atom atom2 = new Atom();
		atom2.addParticleGroupWith(p, Spin.DOWN);

		assertThat(atom1.getHash()).isNotEqualTo(atom2.getHash());
	}
}
