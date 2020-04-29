package com.radixdlt.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongFunction;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.Test;

public class MathUtilsTest {
	private static <T> void testPrimeLCM(
		IntFunction<T[]> arrayBuilder,
		LongFunction<T> builder,
		BinaryOperator<T> multiply,
		T one,
		T max,
		Function<T, BigInteger> toBI,
		BiFunction<T, T[], T> lcmFunction
	) {
		List<Integer> primeList = Arrays.asList(
			2, 3, 5, 7, 11, 13, 17, 19, 23, 29,
			31, 37, 41, 43, 47, 53, 59, 61, 67,
			71, 73, 79, 83, 89, 97, 101, 103, 107,
			109, 113, 127, 131, 137, 139, 149
		);
		Function<Integer, LongStream> primeNumbers = size -> primeList.stream()
			.mapToLong(i -> i)
			.limit(size);

		BigInteger maxBI = toBI.apply(max);
		for (int size = 1; size < primeList.size(); size++) {
			T[] values = primeNumbers.apply(size)
				.mapToObj(builder)
				.toArray(arrayBuilder);

			BigInteger biExpected = primeNumbers.apply(size)
				.mapToObj(BigInteger::valueOf)
				.reduce(BigInteger.ONE, BigInteger::multiply);

			final T expected;
			if (biExpected.compareTo(maxBI) > 0) {
				expected = null;
			} else {
				expected = primeNumbers.apply(size)
					.mapToObj(builder)
					.reduce(one, multiply);
			}

			assertThat(lcmFunction.apply(max, values))
				.isEqualTo(expected);
		}
	}

	private static <T> void testGeometricLCM(
		IntFunction<T[]> arrayBuilder,
		LongFunction<T> builder,
		BinaryOperator<T> multiply,
		BiFunction<T, Integer, T> pow,
		T max,
		Function<T, BigInteger> toBI,
		BiFunction<T, T[], T> lcmFunction
	) {

		BigInteger maxBI = toBI.apply(max);
		for (int exponent = 1; exponent < 12; exponent++) {
			for (int base = 2; base < 1024; base++) {
				final T baseNum = builder.apply(base);
				final T[] values = Stream.iterate(baseNum, n -> multiply.apply(n, baseNum))
					.limit(exponent)
					.toArray(arrayBuilder);

				final BigInteger biExpected = BigInteger.valueOf(base).pow(exponent);
				final T expected;
				if (biExpected.compareTo(maxBI) > 0) {
					expected = null;
				} else {
					expected = pow.apply(builder.apply(base), exponent);
				}

				assertThat(lcmFunction.apply(max, values))
					.isEqualTo(expected);
			}
		}
	}

	@Test
	public void testOneAndMax128() {
		assertThat(MathUtils.cappedLCM(UInt128.MAX_VALUE, UInt128.ONE, UInt128.MAX_VALUE))
			.isEqualTo(UInt128.MAX_VALUE);
	}

	@Test
	public void testPrimeLCM128() {
		testPrimeLCM(
			UInt128[]::new,
			UInt128::from,
			UInt128::multiply,
			UInt128.ONE,
			UInt128.MAX_VALUE,
			i -> new BigInteger(1, i.toByteArray()),
			MathUtils::cappedLCM
		);
	}

	@Test
	public void testGeometricLCM128() {
		testGeometricLCM(
			UInt128[]::new,
			UInt128::from,
			UInt128::multiply,
			UInt128::pow,
			UInt128.MAX_VALUE,
			i -> new BigInteger(1, i.toByteArray()),
			MathUtils::cappedLCM
		);
	}

	@Test
	public void testOneAndMax256() {
		assertThat(MathUtils.cappedLCM(UInt256.MAX_VALUE, UInt256.ONE, UInt256.MAX_VALUE))
			.isEqualTo(UInt256.MAX_VALUE);
	}

	@Test
	public void testPrimeLCM256() {
		testPrimeLCM(
			UInt256[]::new,
			UInt256::from,
			UInt256::multiply,
			UInt256.ONE,
			UInt256.MAX_VALUE,
			i -> new BigInteger(1, i.toByteArray()),
			MathUtils::cappedLCM
		);
	}

	@Test
	public void testGeometricLCM256() {
		testGeometricLCM(
			UInt256[]::new,
			UInt256::from,
			UInt256::multiply,
			UInt256::pow,
			UInt256.MAX_VALUE,
			i -> new BigInteger(1, i.toByteArray()),
			MathUtils::cappedLCM
		);
	}
}