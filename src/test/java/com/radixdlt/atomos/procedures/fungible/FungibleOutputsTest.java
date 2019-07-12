package com.radixdlt.atomos.procedures.fungible;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.google.common.collect.Lists;
import com.radixdlt.atoms.Particle;
import com.radixdlt.utils.UInt256;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;

public class FungibleOutputsTest {
	@Test
	public void testCreate() {
		// valid simple create
		FungibleOutputs.of(Stream.of(
			new Fungible(mock(Particle.class), UInt256.ONE, 0)));

		// valid create with overlapping fungibles
		Particle particle = mock(Particle.class);
		FungibleOutputs.of(Stream.of(
			new Fungible(particle, UInt256.ONE, 0),
			new Fungible(particle, UInt256.TWO, 0),
			new Fungible(mock(Particle.class), UInt256.THREE, 1))
		);
	}

	@Test
	public void testEmpty() {
		assertTrue(FungibleOutputs.of(Stream.of()).isEmpty());
		assertFalse(FungibleOutputs.of(Stream.of(new Fungible(mock(Particle.class), UInt256.ONE, 0))).isEmpty());
	}

	@Test
	public void testGroup() {
		Particle particle0 = mock(Particle.class);
		Particle particle1 = mock(Particle.class);
		Fungible consumer0 = new Fungible(particle0, UInt256.ONE, 0);
		Fungible consumer1 = new Fungible(particle1, UInt256.FOUR, 1);
		FungibleOutputs fungibleOutputs = FungibleOutputs.of(Stream.of(
			consumer0,
			consumer1
		));

		Map<Particle, FungibleOutputs> grouped = fungibleOutputs.group(Fungible::getParticle);
		assertThat(Lists.newArrayList(grouped.keySet().iterator())).containsExactlyInAnyOrder(
			particle0,
			particle1
		);
		assertThat(grouped.get(particle0).fungibles()).containsExactly(
			consumer0
		);
		assertThat(grouped.get(particle1).fungibles()).containsExactly(
			consumer1
		);
	}

	@Test
	public void testConsumerAmount() {
		Particle particle0 = mock(Particle.class);
		Particle particle1 = mock(Particle.class);
		FungibleOutputs fungibleOutputs = FungibleOutputs.of(Stream.of(
			new Fungible(particle0, UInt256.ONE, 0),
			new Fungible(particle0, UInt256.TWO, 0),
			new Fungible(particle1, UInt256.FOUR, 1))
		);
		assertEquals(UInt256.THREE, fungibleOutputs.amount(particle0));
		assertEquals(UInt256.FOUR, fungibleOutputs.amount(particle1));
		assertEquals(UInt256.ZERO, fungibleOutputs.amount(mock(Particle.class)));
	}

	@Test
	public void testContains() {
		Particle containedParticleA = mock(Particle.class);
		Particle containedParticleB = mock(Particle.class);
		Particle missingParticle = mock(Particle.class);

		FungibleOutputs fungibleOutputs = FungibleOutputs.of(Stream.of(
			new Fungible(containedParticleA, UInt256.ONE, 0),
			new Fungible(containedParticleB, UInt256.ONE, 1)
		));

		assertTrue(fungibleOutputs.contains(containedParticleA));
		assertTrue(fungibleOutputs.contains(containedParticleB));
		assertFalse(fungibleOutputs.contains(missingParticle));
	}

	@Test
	public void testOrder() {
		Fungible fungible0 = new Fungible(mock(Particle.class), UInt256.ONE, 0);
		Fungible fungible2 = new Fungible(mock(Particle.class), UInt256.ONE, 2);
		Fungible fungible1 = new Fungible(mock(Particle.class), UInt256.ONE, 1);
		FungibleOutputs fungibleOutputs = FungibleOutputs.of(Stream.of(
			fungible0,
			fungible2,
			fungible1
		));

		Assert.assertArrayEquals(new Fungible[]{fungible0, fungible1, fungible2},
			fungibleOutputs.fungibles().toArray(Fungible[]::new));
	}

	private static abstract class Oxygen extends Particle {
	}

	private static abstract class Hydrogen extends Particle {
	}

	private static abstract class Other extends Particle {
	}

	@Test
	public void testConsumeNull() {
		assertThatThrownBy(() -> FungibleOutputs.of(Stream.empty()).consume(null));
	}

	@Test
	public void testConsumeExact() {
		Oxygen oxygen0 = mock(Oxygen.class);
		Hydrogen hydrogen0 = mock(Hydrogen.class);
		FungibleOutputs fungibleOutputs = FungibleOutputs.of(Stream.of(
			new Fungible(oxygen0, Oxygen.class, UInt256.FIVE, 0),
			new Fungible(hydrogen0, Hydrogen.class, UInt256.TEN, 1)
		));
		FungibleInputs fungibleInputs = FungibleInputs.of(Stream.of(
			new Fungible(oxygen0, Oxygen.class, UInt256.FIVE, 0),
			new Fungible(hydrogen0, Hydrogen.class, UInt256.TEN, 1)
		));

		FungibleInputs remaining = fungibleOutputs.consume(fungibleInputs);

		assertTrue(remaining.isEmpty());
	}

	@Test
	public void testConsumeExactWithOther() {
		Oxygen oxygen0 = mock(Oxygen.class);
		Hydrogen hydrogen0 = mock(Hydrogen.class);
		Other other0 = mock(Other.class);
		FungibleOutputs fungibleOutputs = FungibleOutputs.of(Stream.of(
			new Fungible(oxygen0, Oxygen.class, UInt256.FIVE, 0),
			new Fungible(hydrogen0, Hydrogen.class, UInt256.TEN, 1)
		));
		FungibleInputs fungibleInputs = FungibleInputs.of(Stream.of(
			new Fungible(oxygen0, Oxygen.class, UInt256.FIVE, 0),
			new Fungible(hydrogen0, Hydrogen.class, UInt256.TEN, 1),
			new Fungible(other0, Other.class, UInt256.TEN, 2)
		));

		FungibleInputs remaining = fungibleOutputs.consume(fungibleInputs);

		Assertions.assertThat(remaining.fungibles()).containsExactly(
			new Fungible(other0, Other.class, UInt256.TEN, 2)
		);
	}

	@Test
	public void testConsumeExactWithTooManyConsumables() {
		Oxygen oxygen0 = mock(Oxygen.class);
		Hydrogen hydrogen0 = mock(Hydrogen.class);

		FungibleOutputs fungibleOutputs = FungibleOutputs.of(Stream.of(
			new Fungible(oxygen0, Oxygen.class, UInt256.FIVE, 0),
			new Fungible(hydrogen0, Hydrogen.class, UInt256.TEN, 1)
		));
		FungibleInputs fungibleInputs = FungibleInputs.of(Stream.of(
			new Fungible(oxygen0, Oxygen.class, UInt256.FIVE, 0),
			new Fungible(hydrogen0, Hydrogen.class, UInt256.from(15), 1)
		));

		FungibleInputs remaining = fungibleOutputs.consume(fungibleInputs);

		Assertions.assertThat(remaining.fungibles()).containsExactly(
			new Fungible(hydrogen0, Hydrogen.class, UInt256.FIVE, 1)
		);
	}

	@Test
	public void testConsumeExactWithTooFewConsumables() {
		Oxygen oxygen0 = mock(Oxygen.class);
		Hydrogen hydrogen0 = mock(Hydrogen.class);

		FungibleOutputs fungibleOutputs = FungibleOutputs.of(Stream.of(
			new Fungible(oxygen0, Oxygen.class, UInt256.FIVE, 0),
			new Fungible(hydrogen0, Hydrogen.class, UInt256.TEN, 1)
		));
		FungibleInputs fungibleInputs = FungibleInputs.of(Stream.of(
			new Fungible(oxygen0, Oxygen.class, UInt256.FIVE, 0),
			new Fungible(hydrogen0, Hydrogen.class, UInt256.FIVE, 1)
		));

		Assertions.assertThatThrownBy(() -> fungibleOutputs.consume(fungibleInputs))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("not enough")
			.hasMessageContaining("10");
	}

	@Test
	public void testConsumeWithUnexpectedConsumables() {
		Oxygen oxygen0 = mock(Oxygen.class);
		Hydrogen hydrogen0 = mock(Hydrogen.class);

		FungibleOutputs fungibleOutputs = FungibleOutputs.of(Stream.of(
			new Fungible(oxygen0, Oxygen.class, UInt256.FIVE, 0),
			new Fungible(hydrogen0, Hydrogen.class, UInt256.TEN, 1)
		));
		FungibleInputs fungibleInputs = FungibleInputs.of(Stream.of(
			new Fungible(oxygen0, Oxygen.class, UInt256.FIVE, 0),
			new Fungible(mock(Hydrogen.class), Hydrogen.class, UInt256.FIVE, 1)
		));

		Assertions.assertThatThrownBy(() -> fungibleOutputs.consume(fungibleInputs))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Unexpected consumer");
	}

	@Test
	public void testOf() {
		Particle particle0 = mock(Particle.class);
		Particle particle1 = mock(Particle.class);
		Fungible output0 = new Fungible(particle0, UInt256.ONE, 0);
		Fungible output1 = new Fungible(particle1, UInt256.FOUR, 1);
		FungibleOutputs fungibleOutputs0 = FungibleOutputs.of(Stream.of(output0));
		FungibleOutputs fungibleOutputs1 = FungibleOutputs.of(Stream.of(output1));

		assertThat(FungibleOutputs.of(fungibleOutputs1, fungibleOutputs0).fungibles()).containsExactly(
			output0,
			output1
		);
	}
}