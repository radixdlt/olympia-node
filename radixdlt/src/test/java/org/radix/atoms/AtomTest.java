package org.radix.atoms;

import com.radixdlt.common.Atom;
import com.radixdlt.constraintmachine.Spin;
import org.junit.Test;

public class AtomTest {
	@Test
	public void testNullParticles() {
		Atom atom = new Atom();
		atom.spunParticles();
		atom.getParticleGroupCount();
		atom.particles(Spin.UP);
	}
}