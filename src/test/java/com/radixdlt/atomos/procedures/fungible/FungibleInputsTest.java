package com.radixdlt.atomos.procedures.fungible;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.radixdlt.atomos.FungibleComposition;
import com.radixdlt.atoms.Particle;
import com.radixdlt.utils.UInt256;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Test;

public class FungibleInputsTest {
	public static final FungibleInputs FUNGIBLE_INPUTS_EMPTY = FungibleInputs.of(Stream.empty());

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

	private abstract static class Oxygen extends Particle {
	}

	private abstract static class Hydrogen extends Particle {
	}

	private abstract static class Uranium extends Particle {
	}

	private abstract static class H2O extends Particle {
	}

	private static final FungibleComposition COMPOSITION_H2O = FungibleComposition.of(
		2, Hydrogen.class,
		1, Oxygen.class
	);

	private static FungibleInputs inputsOther() {
		return FungibleInputs.of(Stream.of(
			new Fungible(mock(Uranium.class), UInt256.TWO, 0),
			new Fungible(mock(Uranium.class), UInt256.THREE, 10)
		));
	}

	private static FungibleInputs inputsEmpty() {
		return FungibleInputs.of(Stream.empty());
	}

	@Test
	public void testMatchEmpty() {
		FungibleInputs.CompositionMatch match = inputsEmpty().match(UInt256.ZERO, COMPOSITION_H2O);
		assertTrue(match.getMatchedInputs().isEmpty());
		assertEquals(UInt256.ZERO, match.getSatisfiedAmount());

		match = inputsEmpty().match(UInt256.TEN, COMPOSITION_H2O);
		assertTrue(match.getMatchedInputs().isEmpty());
		assertEquals(UInt256.ZERO, match.getSatisfiedAmount());

		match = inputsEmpty().match(UInt256.TEN, COMPOSITION_HYDROGEN_DECAY);
		assertTrue(match.getMatchedInputs().isEmpty());
		assertEquals(UInt256.ZERO, match.getSatisfiedAmount());
	}

	@Test
	public void testMatchOther() {
		FungibleInputs.CompositionMatch match = inputsOther().match(UInt256.ZERO, COMPOSITION_H2O);
		assertTrue(match.getMatchedInputs().isEmpty());
		assertEquals(UInt256.ZERO, match.getSatisfiedAmount());

		match = inputsOther().match(UInt256.TEN, COMPOSITION_H2O);
		assertTrue(match.getMatchedInputs().isEmpty());
		assertEquals(UInt256.ZERO, match.getSatisfiedAmount());

		match = inputsOther().match(UInt256.TEN, COMPOSITION_HYDROGEN_DECAY);
		assertTrue(match.getMatchedInputs().isEmpty());
		assertEquals(UInt256.ZERO, match.getSatisfiedAmount());
	}

	private static <T extends Particle> Fungible mockFungible(Class<T> cls, int amount, int index) {
		return new Fungible(mock(cls), cls, UInt256.from(amount), index);
	}

	@Test
	public void testMatchExact() {
		Oxygen oxygen0 = mock(Oxygen.class);
		Hydrogen hydrogen1 = mock(Hydrogen.class);
		Oxygen oxygen2 = mock(Oxygen.class);
		FungibleInputs fungibleInputs = FungibleInputs.of(Stream.of(
			new Fungible(oxygen0, Oxygen.class, UInt256.from(25), 0),
			new Fungible(oxygen0, Oxygen.class, UInt256.from(50), 0),
			new Fungible(oxygen2, Oxygen.class, UInt256.from(25), 2),
			new Fungible(hydrogen1, Hydrogen.class, UInt256.from(100), 1),
			new Fungible(hydrogen1, Hydrogen.class, UInt256.from(100), 1)
		));

		// total of 100 oxygen and 200 hydrogen, so we can get 100 exactly with no remainders
		FungibleInputs.CompositionMatch match = fungibleInputs.match(UInt256.from(100), COMPOSITION_H2O);
		assertEquals(UInt256.from(100), match.getSatisfiedAmount());

		// fungibleInputs should be consumed greedily and in order lowest -> highest index
		assertThat(match.getMatchedInputs().fungibles()).containsExactly(
			new Fungible(oxygen0, Oxygen.class, UInt256.from(25), 0),
			new Fungible(oxygen0, Oxygen.class, UInt256.from(50), 0),
			new Fungible(hydrogen1, Hydrogen.class, UInt256.from(100), 1),
			new Fungible(hydrogen1, Hydrogen.class, UInt256.from(100), 1),
			new Fungible(oxygen2, Oxygen.class, UInt256.from(25), 2)
		);
	}

	@Test
	public void testMatchExactZero() {
		Oxygen oxygen0 = mock(Oxygen.class);
		Hydrogen hydrogen1 = mock(Hydrogen.class);
		Oxygen oxygen2 = mock(Oxygen.class);
		FungibleInputs fungibleInputs = FungibleInputs.of(Stream.of(
			new Fungible(oxygen0, Oxygen.class, UInt256.from(25), 0),
			new Fungible(oxygen0, Oxygen.class, UInt256.from(50), 0),
			new Fungible(oxygen2, Oxygen.class, UInt256.from(25), 2),
			new Fungible(hydrogen1, Hydrogen.class, UInt256.from(100), 1),
			new Fungible(hydrogen1, Hydrogen.class, UInt256.from(100), 1)
		));

		FungibleInputs.CompositionMatch match = fungibleInputs.match(UInt256.from(0), COMPOSITION_H2O);
		assertEquals(FungibleInputs.CompositionMatch.EMPTY, match);
		assertEquals(InputsOutputsMatch.EMPTY, match.withConsumer(mock(Fungible.class)));
	}

	@Test
	public void testMatchExactWithOther() {
		Oxygen oxygen0 = mock(Oxygen.class);
		Hydrogen hydrogen1 = mock(Hydrogen.class);
		Oxygen oxygen2 = mock(Oxygen.class);
		FungibleInputs fungibleInputs = FungibleInputs.of(Stream.of(
			new Fungible(oxygen0, Oxygen.class, UInt256.from(25), 0),
			new Fungible(oxygen0, Oxygen.class, UInt256.from(50), 0),
			new Fungible(oxygen2, Oxygen.class, UInt256.from(25), 2),
			new Fungible(hydrogen1, Hydrogen.class, UInt256.from(100), 1),
			new Fungible(hydrogen1, Hydrogen.class, UInt256.from(100), 1),
			new Fungible(mock(Uranium.class), Uranium.class, UInt256.TWO, 3),
			new Fungible(mock(Uranium.class), Uranium.class, UInt256.THREE, 4)
		));

		// total of 100 oxygen and 200 hydrogen, so we can get 100 exactly with no remainders
		FungibleInputs.CompositionMatch match = fungibleInputs.match(UInt256.from(100), COMPOSITION_H2O);
		assertEquals(UInt256.from(100), match.getSatisfiedAmount());

		// fungibleInputs should be consumed greedily and in order lowest -> highest index
		assertThat(match.getMatchedInputs().fungibles()).containsExactly(
			new Fungible(oxygen0, Oxygen.class, UInt256.from(25), 0),
			new Fungible(oxygen0, Oxygen.class, UInt256.from(50), 0),
			new Fungible(hydrogen1, Hydrogen.class, UInt256.from(100), 1),
			new Fungible(hydrogen1, Hydrogen.class, UInt256.from(100), 1),
			new Fungible(oxygen2, Oxygen.class, UInt256.from(25), 2)
		);
	}

	@Test
	public void testMatchTooManyConsumables() {
		Oxygen oxygen0 = mock(Oxygen.class);
		Hydrogen hydrogen1 = mock(Hydrogen.class);
		Oxygen oxygen2 = mock(Oxygen.class);
		Oxygen oxygen3 = mock(Oxygen.class);
		FungibleInputs fungibleInputs = FungibleInputs.of(Stream.of(
			new Fungible(oxygen0, Oxygen.class, UInt256.from(25), 0),
			new Fungible(oxygen0, Oxygen.class, UInt256.from(50), 0),
			// 25 too many Oxygen
			new Fungible(oxygen2, Oxygen.class, UInt256.from(50), 2),
			new Fungible(oxygen3, Oxygen.class, UInt256.from(25), 3),
			new Fungible(hydrogen1, Hydrogen.class, UInt256.from(100), 1),
			// 50 too many hydrogen
			new Fungible(hydrogen1, Hydrogen.class, UInt256.from(150), 1)
		));

		// total of 125 oxygen and 250 hydrogen, so we can get 100 instead of a possible 125 (150 H + 75 O)
		FungibleInputs.CompositionMatch match = fungibleInputs.match(UInt256.from(100), COMPOSITION_H2O);
		assertEquals(UInt256.from(100), match.getSatisfiedAmount());

		// fungibleInputs should be consumed greedily and in order lowest -> highest index
		assertThat(match.getMatchedInputs().fungibles()).containsExactly(
			new Fungible(oxygen0, Oxygen.class, UInt256.from(25), 0),
			new Fungible(oxygen0, Oxygen.class, UInt256.from(50), 0),
			new Fungible(hydrogen1, Hydrogen.class, UInt256.from(100), 1),
			new Fungible(hydrogen1, Hydrogen.class, UInt256.from(100), 1),
			new Fungible(oxygen2, Oxygen.class, UInt256.from(25), 2)
		);
	}

	@Test
	public void testMatchTooFewConsumables() {
		Oxygen oxygen0 = mock(Oxygen.class);
		Hydrogen hydrogen1 = mock(Hydrogen.class);
		Oxygen oxygen2 = mock(Oxygen.class);
		FungibleInputs fungibleInputs = FungibleInputs.of(Stream.of(
			new Fungible(oxygen0, Oxygen.class, UInt256.from(30), 0),
			new Fungible(oxygen0, Oxygen.class, UInt256.from(50), 0),
			// 10 too few oxygen
			new Fungible(oxygen2, Oxygen.class, UInt256.from(10), 2),
			new Fungible(hydrogen1, Hydrogen.class, UInt256.from(100), 1),
			// 50 too few hydrogen
			new Fungible(hydrogen1, Hydrogen.class, UInt256.from(50), 1)
		));

		// total of 90 oxygen and 150 hydrogen, so we can only get 75 instead of requested 100 (150 H + 75 O)
		FungibleInputs.CompositionMatch match = fungibleInputs.match(UInt256.from(100), COMPOSITION_H2O);
		assertEquals(UInt256.from(75), match.getSatisfiedAmount());

		// fungibleInputs should be consumed greedily and in order lowest -> highest index
		assertThat(match.getMatchedInputs().fungibles()).containsExactly(
			new Fungible(oxygen0, Oxygen.class, UInt256.from(30), 0),
			new Fungible(oxygen0, Oxygen.class, UInt256.from(45), 0),
			new Fungible(hydrogen1, Hydrogen.class, UInt256.from(100), 1),
			new Fungible(hydrogen1, Hydrogen.class, UInt256.from(50), 1)
		);
	}

	// yes I know Hydrogen is stable, let's just pretend
	private static final FungibleComposition COMPOSITION_HYDROGEN_DECAY = FungibleComposition.of(
		2, Hydrogen.class
	);

	@Test
	public void testMatchDecay() {
		final UInt256 startAmount = UInt256.from(512);
		final UInt256 targetAmount = UInt256.ONE;

		Hydrogen hydrogen0 = mock(Hydrogen.class);
		FungibleInputs fungibleInputs = FungibleInputs.of(Stream.of(
			new Fungible(hydrogen0, Hydrogen.class, startAmount, 0)
		));

		UInt256 currentAmount = startAmount;
		while (!currentAmount.equals(targetAmount)) {
			UInt256 nextAmount = currentAmount.divide(UInt256.TWO);
			FungibleInputs.CompositionMatch match = fungibleInputs.match(nextAmount, COMPOSITION_HYDROGEN_DECAY);
			assertEquals(nextAmount, match.getSatisfiedAmount());

			assertThat(match.getMatchedInputs().fungibles()).containsExactly(
				new Fungible(hydrogen0, Hydrogen.class, currentAmount, 0)
			);
			fungibleInputs = FungibleInputs.of(Stream.of(new Fungible(hydrogen0, Hydrogen.class, nextAmount, 0)));
			currentAmount = nextAmount;
		}
	}

	@Test
	public void testMatchIncompleteApproved() {
		Oxygen oxygen0 = mock(Oxygen.class);
		FungibleInputs fungibleInputs = FungibleInputs.of(Stream.of(
			new Fungible(oxygen0, Oxygen.class, UInt256.from(30), 0),
			new Fungible(oxygen0, Oxygen.class, UInt256.from(50), 0)
		));

		// total of 90 oxygen and 150 hydrogen, so we can only get 75 instead of requested 100 (150 H + 75 O)
		assertThatThrownBy(() -> fungibleInputs.match(UInt256.from(100), COMPOSITION_H2O, Maps.newHashMap()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("not assigned to");
	}
}