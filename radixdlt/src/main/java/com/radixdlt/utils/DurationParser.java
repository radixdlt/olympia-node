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
 *
 */

package com.radixdlt.utils;

import com.google.common.collect.ImmutableMap;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Optional.ofNullable;

/**
 * Utility method for parsing strings containing description of duration.
 *
 * Recognized string format:
 * <pre>
 *     [0-9]+(s|m|h)?
 * </pre>
 * Leading number represents value duration and must be greater than zero. Fractional values are not supported.
 * Optional trailing character defines units of duration value:
 * <ul>
 *     <li><b>s</b> - second(s)</li>
 *     <li><b>m</b> - minute(s)</li>
 *     <li><b>h</b> - hour(s)</li>
 * </ul>
 * If units are omitted or not recognized then <b>seconds</b> are assumed by default.
 */
public final class DurationParser {
	private DurationParser() {
	}

	private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)(\\w)?");
	private static final String DEFAULT_UNITS = "s";
	private static final Map<String, TemporalUnit> UNIT_MAP = ImmutableMap.of(
			"s", ChronoUnit.SECONDS,
			"m", ChronoUnit.MINUTES,
			"h", ChronoUnit.HOURS
			);

	/**
	 * Parse input string into duration value.
	 *
	 * @param input Input string
	 * @return {@link Optional} with parsed value. If input is empty or value is less or equal to 0 then
	 * empty instance is returned.
	 */
	public static Optional<Duration> parse(String input) {
		Objects.requireNonNull(input);

		final Matcher matcher = DURATION_PATTERN.matcher(input.toLowerCase());

		if (!matcher.find()) {
			return Optional.empty();
		}

		long value = Long.parseLong(matcher.group(1));

		if (value <= 0) {
			return Optional.empty();
		}

		final String units = ofNullable(matcher.group(2)).orElse(DEFAULT_UNITS);

		return ofNullable(UNIT_MAP.get(units)).map(unit -> Duration.of(value, unit));
	}
}
