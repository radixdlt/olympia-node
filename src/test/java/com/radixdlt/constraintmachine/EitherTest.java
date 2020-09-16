package com.radixdlt.constraintmachine;

import java.util.NoSuchElementException;
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

	@Test
	public void testOrElseNoThrow() {
		Either<Integer, String> first = Either.first(12);

		assertEquals(12, first.orElseThrow(() -> new AssertionError()).intValue());
		assertEquals(12, first.orElseThrow(s -> new AssertionError(s)).intValue());
	}

	@Test(expected = IllegalStateException.class)
	public void testOrElseThrow1() {
		Either.second("foo").orElseThrow(() -> new IllegalStateException());
	}

	@Test(expected = IllegalStateException.class)
	public void testOrElseThrow2() {
		Either.second("bar").orElseThrow(s -> new IllegalStateException(s));
	}

	@Test(expected = NoSuchElementException.class)
	public void testThrowingGetFirst() {
		Either.second("baz").get();
	}

	@Test(expected = NoSuchElementException.class)
	public void testThrowingGetSecond() {
		Either.first("bie").getSecond();
	}

	@Test
	public void testSwap() {
		Either<Integer, String> first = Either.first(12);
		Either<Integer, String> second = Either.second("Hello");

		assertEquals(Either.second(12), first.swap());
		assertEquals(Either.first("Hello"), second.swap());
	}

	@Test
	public void testIterator() {
		Either<Integer, String> first = Either.first(12);
		Either<Integer, String> second = Either.second("Hello");

		assertTrue(first.iterator().hasNext());
		assertFalse(second.iterator().hasNext());

		assertEquals(12, first.iterator().next().intValue());
	}

	@Test
	public void testOrElseEtc() {
		Either<Integer, String> first = Either.first(12);
		Either<Integer, String> second = Either.second("Hello");

		assertEquals(first, first.orElse(second));
		assertEquals(first, second.orElse(first));

		assertEquals(first, first.orElseGet(() -> second));
		assertEquals(first, second.orElseGet(() -> first));
	}

	@Test
	public void sensibleToString() {
		String firstString = Either.first("Hello").toString();
		String secondString = Either.second("Bye").toString();

		assertTrue(firstString.contains(Either.First.class.getSimpleName()));
		assertTrue(firstString.contains("Hello"));
		assertTrue(secondString.contains(Either.Second.class.getSimpleName()));
		assertTrue(secondString.contains("Bye"));
	}
}
