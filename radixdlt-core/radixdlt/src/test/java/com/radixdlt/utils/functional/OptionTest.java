/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */
package com.radixdlt.utils.functional;

import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class OptionTest {
	@Test
	public void emptyOptionCanBeCreated() {
		Option.empty()
			.apply(() -> {}, v -> fail());
	}

	@Test
	public void optionWithDataCanBeCreated() {
		Option.option("not empty")
			.apply(Assert::fail, v -> assertEquals("not empty", v));
	}

	@Test
	public void nonEmptyOptionCanBeMappedToOtherOption() {
		Option.option(123)
			.whenEmpty(Assert::fail)
			.whenPresent(v -> assertEquals(123, (int) v))
			.map(Object::toString)
			.whenEmpty(Assert::fail)
			.whenPresent(v -> assertEquals("123", v));
	}

	@Test
	public void emptyOptionRemainsEmptyAfterMapping() {
		Option.empty()
			.whenPresent(v -> fail())
			.map(Object::toString)
			.whenPresent(v -> fail());
	}

	@Test
	public void nonEmptyOptionCanBeFlatMappedIntoOtherOption() {
		Option.option(345)
			.whenEmpty(Assert::fail)
			.whenPresent(v -> assertEquals(345, (int) v))
			.flatMap(val -> Option.option(val + 2))
			.whenEmpty(Assert::fail)
			.whenPresent(v -> assertEquals(347, (int) v));
	}

	@Test
	public void emptyOptionRemainsEmptyAndNotFlatMapped() {
		Option.empty()
			.whenPresent(v -> fail())
			.flatMap(val -> Option.option("not empty"))
			.whenPresent(v -> fail());
	}

	@Test
	public void logicalOrChoosesFirsNonEmptyOption1() {
		final var firstNonEmpty = Option.option("1");
		final var secondNonEmpty = Option.option("2");
		final var firstEmpty = Option.<String>empty();
		final var secondEmpty = Option.<String>empty();

		assertEquals(firstNonEmpty, firstNonEmpty.or(() -> secondNonEmpty));
		assertEquals(firstNonEmpty, firstEmpty.or(() -> firstNonEmpty));
		assertEquals(firstNonEmpty, firstEmpty.or(() -> firstNonEmpty).or(() -> secondNonEmpty));
		assertEquals(firstNonEmpty, firstEmpty.or(() -> secondEmpty).or(() -> firstNonEmpty));
	}

	@Test
	public void logicalOrChoosesFirsNonEmptyOption2() {
		final var firstNonEmpty = Option.option("1");
		final var secondNonEmpty = Option.option("2");
		final var firstEmpty = Option.<String>empty();
		final var secondEmpty = Option.<String>empty();

		assertEquals(firstNonEmpty, firstNonEmpty.or(secondNonEmpty));
		assertEquals(firstNonEmpty, firstEmpty.or(firstNonEmpty));
		assertEquals(firstNonEmpty, firstEmpty.or(firstNonEmpty).or(secondNonEmpty));
		assertEquals(firstNonEmpty, firstEmpty.or(secondEmpty).or(firstNonEmpty));
	}

	@Test
	public void otherwiseProvidesValueIfOptionIsEmpty() {
		assertEquals(123, Option.empty().otherwise(123));
		assertEquals(123, Option.empty().otherwiseGet(() -> 123));
	}

	@Test
	public void otherwiseIsIgnoredProvidesValueIfOptionIsNotEmpty() {
		assertEquals(234, (int) Option.option(234).otherwise(123));
		assertEquals(234, (int) Option.option(234).otherwiseGet(() -> 123));
	}

	@Test
	public void optionCanBeStreamed() {
		assertTrue(Option.empty().stream().findFirst().isEmpty());
		assertEquals(123, (int) Option.option(123).stream().findFirst().orElse(321));
	}

	@Test
	public void nonEmptyInstanceCanBeFiltered() {
		Option.option(123)
			.whenEmpty(Assert::fail)
			.filter(val -> val > 1)
			.whenEmpty(Assert::fail)
			.filter(val -> val < 100)
			.whenPresent(val -> fail());
	}

	@Test
	public void emptyInstanceRemainsEmptyAfterFilteringAndPredicateIsNotInvoked() {
		Option.empty()
			.whenPresent(v -> fail())
			.filter(v -> true)
			.whenPresent(v -> fail());
	}

	@Test
	public void emptyInstancesAreEqual() {
		assertFalse(Option.empty().equals(""));
		assertEquals(Option.empty(), Option.option(null));
	}

	@Test
	public void nonEmptyInstancesAreEqual() {
		assertFalse(Option.option(1).equals(1));
		assertEquals(Option.option(1), Option.option(1));
	}

	@Test
	public void optionCanBePutInMap() {
		final var map = Map.of(1, Option.option(1), 2, Option.option(2));

		assertEquals(Option.option(1), map.get(1));
		assertEquals(Option.option(2), map.get(2));
	}
}
