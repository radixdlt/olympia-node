package com.radixdlt.atoms;

import org.junit.Test;
import org.radix.atoms.Atom;

public class AtomTest {
	@Test
	public void testNullParticles() {
		Atom atom = new Atom();
		atom.spunParticles();
		atom.getParticleGroupCount();
		atom.particles(Spin.UP);
	}
}