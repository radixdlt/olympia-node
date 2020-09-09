/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package org.radix.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Optional.ofNullable;

/**
 * Utility class convenient for representation of fixed durations in cases when both,
 * value and time unis are necessary, for example, with
 * {@link java.util.concurrent.CountDownLatch#await(long, TimeUnit)}).
 * <p>
 * Provided {@link #parse(String)} method is able to parse strings which represent a duration.
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
public class Duration {
	private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)(\\w)?");
	private static final String DEFAULT_UNITS = "s";
	private static final Map<String, TimeUnit> UNIT_MAP = new HashMap<>();

	private final long value;
	private final TimeUnit unit;

	static {
		UNIT_MAP.put("s", TimeUnit.SECONDS);
		UNIT_MAP.put("m", TimeUnit.MINUTES);
		UNIT_MAP.put("h", TimeUnit.HOURS);
	}

	private Duration(long value, TimeUnit unit) {
		this.value = value;
		this.unit = unit;
	}

	public <R> R apply(BiFunction<Long, TimeUnit, R> mapper) {
		return mapper.apply(value, unit);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (o instanceof Duration) {
			Duration that = (Duration) o;
			return value == that.value && unit == that.unit;
		}

		return false;
	}

	@Override
	public int hashCode() {
		return (int) (value ^ (value >>> 32)) * 32 + unit.hashCode();
	}

	@Override
	public String toString() {
		return "ParsedDuration(" + value + ", " + unit + ')';
	}

	public static ParsedDurationBuilder of(long value) {
		return new ParsedDurationBuilder(value);
	}

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

		return ofNullable(UNIT_MAP.get(units)).map(unit -> Duration.of(value).units(unit));
	}

	public static class ParsedDurationBuilder {
		private final long value;

		private ParsedDurationBuilder(long value) {
			this.value = value;
		}

		public Duration minutes() {
			return new Duration(value, TimeUnit.MINUTES);
		}

		public Duration seconds() {
			return new Duration(value, TimeUnit.SECONDS);
		}

		public Duration hours() {
			return new Duration(value, TimeUnit.HOURS);
		}

		public Duration units(TimeUnit unit) {
			if (unit == null) {
				throw new IllegalArgumentException("Time unit is mandatory for duration");
			}

			return new Duration(value, unit);
		}
	}
}
