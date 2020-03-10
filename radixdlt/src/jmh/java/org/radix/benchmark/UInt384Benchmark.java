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

package org.radix.benchmark;

import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import com.radixdlt.utils.UInt384;

import com.google.common.math.BigIntegerMath;

/**
 * Some JMH driven benchmarks for testing UInt384 performance.
 * <p>
 * Note that the build system has been set up to make it easier to
 * run these performance tests under gradle.  Using gradle, it should
 * be possible to execute:
 * <pre>
 *    $ gradle clean jmh
 * </pre>
 * from the RadixCode/radixdlt directory.  Note that the JMH plugin
 * does not appear to be super robust, and changes to benchmark tests
 * and other code are not always re-instrumented correctly by gradle
 * daemons.  This can be worked around by avoiding the gradle daemon:
 * <pre>
 *    $ gradle --no-daemon clean jmh
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class UInt384Benchmark {

	private static final BigInteger BI_TWO = BigInteger.valueOf(2);
	private static final BigInteger BI_SMALL_VALUE = BI_TWO.pow(27).subtract(BigInteger.ONE);
	private static final BigInteger BI_LARGE_VALUE1 = BI_TWO.pow(UInt384.SIZE - 27).subtract(BigInteger.ONE);
	private static final BigInteger BI_LARGE_VALUE2 = BI_TWO.pow(UInt384.SIZE - 28).subtract(BigInteger.ONE);

	private static final UInt384 UI_SMALL_VALUE = fromBigInt(BI_SMALL_VALUE);
	private static final UInt384 UI_LARGE_VALUE1 = fromBigInt(BI_LARGE_VALUE1);
	private static final UInt384 UI_LARGE_VALUE2 = fromBigInt(BI_LARGE_VALUE2);

    static UInt384 fromBigInt(BigInteger bi) {
    	return UInt384.from(bi.toByteArray());
    }

	@Benchmark
	public void addLargeLargeInt384(Blackhole bh) {
		bh.consume(UI_LARGE_VALUE1.add(UI_LARGE_VALUE2));
	}

	@Benchmark
	public void addLargeLargeBigInt(Blackhole bh) {
		bh.consume(BI_LARGE_VALUE1.add(BI_LARGE_VALUE2));
	}

	@Benchmark
	public void subLargeLargeInt384(Blackhole bh) {
		bh.consume(UI_LARGE_VALUE1.subtract(UI_LARGE_VALUE2));
	}

	@Benchmark
	public void subLargeLargeBigInt(Blackhole bh) {
		bh.consume(BI_LARGE_VALUE1.subtract(BI_LARGE_VALUE2));
	}

	@Benchmark
	public void mulLargeSmallInt384(Blackhole bh) {
		bh.consume(UI_LARGE_VALUE1.multiply(UI_SMALL_VALUE));
	}

	@Benchmark
	public void mulLargeSmallBigInt(Blackhole bh) {
		bh.consume(BI_LARGE_VALUE1.multiply(BI_SMALL_VALUE));
	}

	@Benchmark
	public void divLargeSmallInt384(Blackhole bh) {
		bh.consume(UI_LARGE_VALUE1.divide(UI_SMALL_VALUE));
	}

	@Benchmark
	public void divLargeSmallBigInt(Blackhole bh) {
		bh.consume(BI_LARGE_VALUE1.divide(BI_SMALL_VALUE));
	}

	@Benchmark
	public void sqrtLargeInt384(Blackhole bh) {
		bh.consume(UI_LARGE_VALUE1.isqrt());
	}

	@Benchmark
	public void sqrtLargeBigInt(Blackhole bh) {
		bh.consume(BigIntegerMath.sqrt(BI_LARGE_VALUE1, RoundingMode.FLOOR));
	}

}
