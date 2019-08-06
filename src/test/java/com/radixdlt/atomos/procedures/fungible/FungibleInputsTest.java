package com.radixdlt.atomos.procedures.fungible;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.google.common.collect.Lists;
import com.radixdlt.atoms.Particle;
import com.radixdlt.utils.UInt256;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Test;

public class FungibleInputsTest {
	@Test
	public void testCreate() {
		// valid simple create
		FungibleInputs.of(Stream.of(
			new Fungible(mock(Particle.class), UInt256.ONE, 0)));

		// valid create with overlapping fungibles
		Particle particle = mock(Particle.class);
		FungibleInputs.of(Stream.of(
			new Fungible(particle, UInt256.ONE, 0),
			new Fungible(particle, UInt256.TWO, 0),
			new Fungible(mock(Particle.class), UInt256.THREE, 1))
		);
	}

	@Test
	public void testConsumableAmount() {
		Particle particle0 = mock(Particle.class);
		Particle particle1 = mock(Particle.class);
		FungibleInputs fungibleInputs = FungibleInputs.of(Stream.of(
			new Fungible(particle0, UInt256.ONE, 0),
			new Fungible(particle0, UInt256.TWO, 0),
			new Fungible(particle1, UInt256.FOUR, 1))
		);
		assertEquals(UInt256.THREE, fungibleInputs.amount(particle0));
		assertEquals(UInt256.FOUR, fungibleInputs.amount(particle1));
		assertEquals(UInt256.ZERO,  fungibleInputs.amount(mock(Particle.class)));
	}

	@Test
	public void testGroup() {
		Particle particle0 = mock(Particle.class);
		Particle particle1 = mock(Particle.class);
		Fungible input0 = new Fungible(particle0, UInt256.ONE, 0);
		Fungible input1 = new Fungible(particle1, UInt256.FOUR, 1);
		FungibleInputs fungibleInputs = FungibleInputs.of(Stream.of(
			input0,
			input1
		));

		Map<Particle, FungibleInputs> grouped = fungibleInputs.group(Fungible::getParticle);
		assertThat(Lists.newArrayList(grouped.keySet().iterator())).containsExactlyInAnyOrder(
			particle0,
			particle1
		);
		assertThat(grouped.get(particle0).fungibles()).containsExactly(
			input0
		);
		assertThat(grouped.get(particle1).fungibles()).containsExactly(
			input1
		);
	}

	@Test
	public void testEmpty() {
		assertTrue(FungibleInputs.of(Stream.of()).isEmpty());
		assertFalse(FungibleInputs.of(Stream.of(new Fungible(mock(Particle.class), UInt256.ONE, 0))).isEmpty());
	}

	@Test
	public void testContains() {
		Particle containedParticleA = mock(Particle.class);
		Particle containedParticleB = mock(Particle.class);
		Particle missingParticle = mock(Particle.class);

		FungibleInputs fungibleInputs = FungibleInputs.of(Stream.of(
			new Fungible(containedParticleA, UInt256.ONE, 0),
			new Fungible(containedParticleB, UInt256.ONE, 1)
		));

		assertTrue(fungibleInputs.contains(containedParticleA));
		assertTrue(fungibleInputs.contains(containedParticleB));
		assertFalse(fungibleInputs.contains(missingParticle));
	}

	@Test
	public void testOrder() {
		Fungible fungible0 = new Fungible(mock(Particle.class), UInt256.ONE, 0);
		Fungible fungible2 = new Fungible(mock(Particle.class), UInt256.ONE, 2);
		Fungible fungible1 = new Fungible(mock(Particle.class), UInt256.ONE, 1);
		FungibleInputs fungibleInputs = FungibleInputs.of(Stream.of(
			fungible0,
			fungible2,
			fungible1
		));

		Assert.assertArrayEquals(new Fungible[]{fungible0, fungible1, fungible2},
			fungibleInputs.fungibles().toArray(Fungible[]::new));
	}
}