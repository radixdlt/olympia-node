/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.utils;

import org.junit.Test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class DurationParserTest {
	@Test
	public void correctDurationsAreParsedProperly() {
		assertEquals(Duration.of(123, ChronoUnit.SECONDS), DurationParser.parse("123").orElseThrow(AssertionError::new));
		assertEquals(Duration.of(321, ChronoUnit.SECONDS), DurationParser.parse("321s").orElseThrow(AssertionError::new));
		assertEquals(Duration.of(213, ChronoUnit.MINUTES), DurationParser.parse("213m").orElseThrow(AssertionError::new));
		assertEquals(Duration.of(132, ChronoUnit.HOURS), DurationParser.parse("132h").orElseThrow(AssertionError::new));
	}

	@Test
	public void fractionalValuesAreTruncated() {
		assertEquals(Duration.of(123, ChronoUnit.SECONDS), DurationParser.parse("123.9").orElseThrow(AssertionError::new));
	}

	@Test
	public void emptyInputResultsToEmptyDuration() {
		assertFalse(DurationParser.parse("").isPresent());
	}

	@Test
	public void nonDigitInputResultsToEmptyDuration() {
		assertFalse(DurationParser.parse("abcdef").isPresent());
	}

	@Test
	public void zeroValueResultsToEmptyDuration() {
		assertFalse(DurationParser.parse("0").isPresent());
	}

	@Test
	public void incorrectUnitsResultToEmptyValue() {
		assertFalse(DurationParser.parse("123f").isPresent());
	}
}