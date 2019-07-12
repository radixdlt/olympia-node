package com.radixdlt.atomos.procedures.fungible;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableList;
import com.radixdlt.atoms.Particle;
import com.radixdlt.utils.UInt256;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;

public class FungibleTest {
	@Test
	public void testCreate() {
		// create valid Fungible
		Particle particle = mock(Particle.class);
		Fungible fungible = new Fungible(particle, UInt256.ONE, 5);
		assertEquals(particle, fungible.getParticle());
		assertEquals(UInt256.ONE, fungible.getAmount());
		assertEquals(5, fungible.getIndex());
		assertEquals(particle.getClass(), fungible.getParticleClass());
		assertFalse(fungible.isZero());

		// particle cannot be null
		assertThatThrownBy(() -> new Fungible(null, UInt256.ONE, 0))
			.isInstanceOf(NullPointerException.class);

		// particle class cannot be null
		assertThatThrownBy(() -> new Fungible(mock(Particle.class), null, UInt256.ONE, 0))
			.isInstanceOf(NullPointerException.class);

		// amount cannot be null
		assertThatThrownBy(() -> new Fungible(mock(Particle.class), null, 0))
			.isInstanceOf(NullPointerException.class);

		// negative indices are not allowed
		assertThatThrownBy(() -> new Fungible(mock(Particle.class), UInt256.ONE, -1))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void testEquals() {
		Particle particle = mock(Particle.class);
		Fungible fungible0 = new Fungible(particle, UInt256.ONE, 5);
		Fungible fungible1 = new Fungible(particle, UInt256.ONE, 5);

		assertEquals(fungible0, fungible1);
		assertNotEquals(fungible0, fungible1.withAmount(UInt256.TWO));
	}

	@Test
	public void testDiffNoOverlap() {
		Particle particle0 = mock(Particle.class);
		Particle particle1 = mock(Particle.class);

		Map<Particle, Fungible> diff = Fungible.diff(
			ImmutableList.of(new Fungible(particle0, UInt256.TWO, 0)),
			ImmutableList.of(new Fungible(particle1, UInt256.TWO, 2)))
			.collect(Collectors.toMap(Fungible::getParticle, f -> f));

		assertEquals(UInt256.TWO, diff.get(particle0).getAmount());
		assertEquals(UInt256.TWO, diff.get(particle1).getAmount());
	}

	@Test
	public void testDiffPartialOverlap() {
		Particle particle0 = mock(Particle.class);
		Particle particle1 = mock(Particle.class);
		Particle particle2 = mock(Particle.class);

		Map<Particle, Fungible> diff = Fungible.diff(
			ImmutableList.of(
				new Fungible(particle0, UInt256.TWO, 0),
				new Fungible(particle1, UInt256.FIVE, 1)
			),
			ImmutableList.of(
				new Fungible(particle1, UInt256.THREE, 1),
				new Fungible(particle2, UInt256.TWO, 2)
			)
		).collect(Collectors.toMap(Fungible::getParticle, f -> f));

		assertEquals(UInt256.TWO, diff.get(particle0).getAmount());
		assertEquals(UInt256.TWO, diff.get(particle1).getAmount());
		assertEquals(UInt256.TWO, diff.get(particle2).getAmount());
	}

	@Test
	public void testDiffCompleteOverlap() {
		Particle particle0 = mock(Particle.class);
		Particle particle1 = mock(Particle.class);
		Particle particle2 = mock(Particle.class);

		ImmutableList<Fungible> a = ImmutableList.of(
			new Fungible(particle0, UInt256.ONE, 0),
			new Fungible(particle1, UInt256.TWO, 1),
			new Fungible(particle2, UInt256.THREE, 2)
		);
		ImmutableList<Fungible> b = ImmutableList.of(
			new Fungible(particle0, UInt256.ONE, 0),
			new Fungible(particle1, UInt256.TWO, 1),
			new Fungible(particle2, UInt256.THREE, 2)
		);

		assertTrue(Fungible.diff(a, b).collect(Collectors.toList()).isEmpty());
	}
}