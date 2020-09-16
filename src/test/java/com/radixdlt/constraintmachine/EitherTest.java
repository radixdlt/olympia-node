package com.radixdlt.constraintmachine;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import static org.junit.Assert.*;

import nl.jqno.equalsverifier.EqualsVerifier;

public class EitherTest {

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(Either.First.class)
			.verify();
		EqualsVerifier.forClass(Either.Second.class)
			.verify();
	}

	@Test
	public void testIsPresentEtc() {
		Either<Integer, String> first = Either.first(12);
		Either<Integer, String> second = Either.second("Hello");

		assertTrue(first.isPresent());
		assertFalse(second.isPresent());

		assertTrue(first.isFirst());
		assertFalse(second.isFirst());

		assertFalse(first.isSecond());
		assertTrue(second.isSecond());

		assertTrue(first.asOptional().isPresent());
		assertFalse(first.asOptionalSecond().isPresent());

		assertFalse(second.asOptional().isPresent());
		assertTrue(second.asOptionalSecond().isPresent());
	}

	@Test
	public void testIfPresent() {
		Either<Integer, String> first = Either.first(12);
		Either<Integer, String> second = Either.second("Hello");

		AtomicBoolean check1 = new AtomicBoolean(false);
		first.ifPresent(f -> check1.set(true));
		assertTrue(check1.get());
		second.ifPresent(f -> fail());

		AtomicBoolean check2 = new AtomicBoolean(false);
		first.ifFirst(f -> check2.set(true));
		assertTrue(check2.get());
		second.ifFirst(f -> fail());

		AtomicBoolean check3 = new AtomicBoolean(false);
		second.ifSecond(f -> check3.set(true));
		assertTrue(check2.get());
		first.ifSecond(f -> fail());
	}

	@Test
	public void testMap() {
		Either<Integer, String> first = Either.first(12);
		Either<Integer, String> second = Either.second("Hello");

		assertEquals(13, first.map(x -> x + 1).get().intValue());
		assertEquals(second, second.map(x -> x + 1));

		assertEquals(first, first.mapSecond(x -> x + "x"));
		assertEquals("Hellox", second.mapSecond(x -> x + "x").getSecond());

		assertEquals(Either.first(13), first.flatMap(x -> Either.first(x + 1)));
		assertEquals(Either.second("Hello"), second.flatMap(x -> Either.first(x + 1)));

		assertEquals(Either.first(12), first.flatMapSecond(x -> Either.second(x + "x")));
		assertEquals(Either.second("Hellox"), second.flatMapSecond(x -> Either.second(x + "x")));

		assertEquals(Either.first(13), first.bimap(x -> x + 1, x -> x + "x"));
		assertEquals(Either.second("Hellox"), second.bimap(x -> x + 1, x -> x + "x"));
	}
}
