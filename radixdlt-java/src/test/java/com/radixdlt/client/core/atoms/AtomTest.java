package com.radixdlt.client.core.atoms;

import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import org.junit.Test;

import java.util.Collections;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AtomTest {
	@Test
	public void when_an_atom_has_multiple_destinations_to_the_same_address__calling_addresses_should_return_one_address() {
		RadixAddress address = mock(RadixAddress.class);
		Particle particle0 = mock(Particle.class);
		when(particle0.getShardables()).thenReturn(Collections.singleton(address));
		Particle particle1 = mock(Particle.class);
		when(particle1.getShardables()).thenReturn(Collections.singleton(address));
		Atom atom = new Atom(ParticleGroup.of(SpunParticle.up(particle0), SpunParticle.up(particle1)), 0L);
		assertThat(atom.addresses()).containsExactly(address);
	}
}