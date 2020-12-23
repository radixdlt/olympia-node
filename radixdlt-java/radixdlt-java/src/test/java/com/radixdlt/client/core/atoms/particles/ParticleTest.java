package com.radixdlt.client.core.atoms.particles;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ParticleTest {

    private static class DummyParticle extends Particle {
    }

    @Test
    public void test_particle_hash() {
        Particle particle = new DummyParticle();
        assertEquals("b0aace23265c295eb13464b5b97cf57d1a227a02c4c7042ab7daae1df1eb6e6a", particle.getHash().toString());
        assertFalse(particle.euid().isZero());
    }
}
