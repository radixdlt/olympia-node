package com.radixdlt.client.core.atoms;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECSignature;
import com.radixdlt.client.core.util.Hash;
import org.junit.Test;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AtomTest {
	@Test
	public void testEmptyAtom() {
		Atom atom = new Atom(Collections.emptyList(), 0L);

		/// The origin of these hashes are this library it self, commit: acbc5307cf5c9f7e1c30300f7438ef5dbc3bb629
		/// These hashes can be used as a reference for other Radix libraries, e.g. Swift.

		assertEquals("e50964da69e6672a98d5e3c1b1d73fb3", atom.getAid().toHexString());
		assertEquals("Two empty atoms should equal", atom, new Atom(Collections.emptyList(), 0L));

		byte[] seed = Hash.sha256("Radix".getBytes(StandardCharsets.UTF_8));
		ECKeyPair ecKeyPair = new ECKeyPair(seed);
		ECSignature signature = ecKeyPair.sign(atom.getHash().toByteArray(), true, true);
		assertEquals("d58c086f3d79a4159241df186306ff21627f09d718820a886110ee927dfc6682", signature.getR().toString(16));
		assertEquals("63a573d3255f529310c6db9a985b01cba72fdcf4236b1e12f64ecac2e2ddce14", signature.getS().toString(16));
	}

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