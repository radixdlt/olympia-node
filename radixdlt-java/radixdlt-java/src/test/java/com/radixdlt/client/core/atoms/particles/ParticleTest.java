package com.radixdlt.client.core.atoms.particles;

import java.util.Set;

import org.junit.Test;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.identifiers.EUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ParticleTest {

    private static class DummyParticle extends Particle {
		@Override
		public Set<EUID> getDestinations() {
			return ImmutableSet.of();
		}
    }

    @Test
    public void test_particle_hash() {
        Particle particle = new DummyParticle();
        assertEquals("b0aace23265c295eb13464b5b97cf57d1a227a02c4c7042ab7daae1df1eb6e6a", particle.getHash().toString());
        assertFalse(particle.euid().isZero());
    }
}
