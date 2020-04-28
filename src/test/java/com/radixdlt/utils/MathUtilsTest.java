package com.radixdlt.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.Test;

public class MathUtilsTest {

	private static final BigInteger UINT128_MAX_BI = new BigInteger(1, UInt128.MAX_VALUE.toByteArray());

	@Test
	public void testOneAndMax() {
		assertThat(MathUtils.cappedLCM(UInt128.MAX_VALUE, UInt128.ONE, UInt128.MAX_VALUE))
			.isEqualTo(UInt128.MAX_VALUE);
	}

	@Test
	public void testPrimeLCM() {
		List<Integer> primeList = Arrays.asList(
			2, 3, 5, 7, 11, 13, 17, 19, 23, 29,
			31, 37, 41, 43, 47, 53, 59, 61, 67,
			71, 73, 79, 83, 89, 97, 101, 103, 107,
			109, 113, 127, 131, 137, 139, 149
		);
		Function<Integer, LongStream> primeNumbers = size -> primeList.stream()
			.mapToLong(i -> i)
			.limit(size);

		for (int size = 1; size < primeList.size(); size++) {
			UInt128[] values = primeNumbers.apply(size)
				.mapToObj(UInt128::from)
				.toArray(UInt128[]::new);

			BigInteger biExpected = primeNumbers.apply(size)
				.mapToObj(BigInteger::valueOf)
				.reduce(BigInteger.ONE, BigInteger::multiply);

			final UInt128 expected;
			if (biExpected.compareTo(UINT128_MAX_BI) > 0) {
				expected = UInt128.MAX_VALUE;
			} else {
				expected = primeNumbers.apply(size)
					.mapToObj(UInt128::from)
					.reduce(UInt128.ONE, UInt128::multiply);
			}

			assertThat(MathUtils.cappedLCM(UInt128.MAX_VALUE, values))
				.isEqualTo(expected);
		}
	}

	@Test
	public void testGeometricLCM() {
		for (int exponent = 1; exponent < 12; exponent++) {
			for (int base = 2; base < 1024; base++) {
				final UInt128 base128 = UInt128.from(base);
				UInt128[] values = Stream.iterate(base128, n -> n.multiply(base128))
					.limit(exponent)
					.toArray(UInt128[]::new);

				final BigInteger biExpected = BigInteger.valueOf(base).pow(exponent);
				final UInt128 expected;
				if (biExpected.compareTo(UINT128_MAX_BI) > 0) {
					expected = UInt128.MAX_VALUE;
				} else {
					expected = UInt128.from(base).pow(exponent);
				}

				assertThat(MathUtils.cappedLCM(UInt128.MAX_VALUE, values))
					.isEqualTo(expected);
			}
		}
	}
}