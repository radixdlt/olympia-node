/* Copyright 2021 Radix DLT Ltd incorporated in England.
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
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
import com.radixdlt.utils.UInt256;

import com.google.common.math.BigIntegerMath;

/**
 * Some JMH driven benchmarks for testing UInt256 performance.
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
public class UInt256Benchmark {

	private static final BigInteger BI_TWO = BigInteger.valueOf(2);
	private static final BigInteger BI_SMALL_VALUE = BI_TWO.pow(27).subtract(BigInteger.ONE);
	private static final BigInteger BI_LARGE_VALUE1 = BI_TWO.pow(UInt256.SIZE - 27).subtract(BigInteger.ONE);
	private static final BigInteger BI_LARGE_VALUE2 = BI_TWO.pow(UInt256.SIZE - 28).subtract(BigInteger.ONE);

	private static final UInt256 UI_SMALL_VALUE = fromBigInt(BI_SMALL_VALUE);
	private static final UInt256 UI_LARGE_VALUE1 = fromBigInt(BI_LARGE_VALUE1);
	private static final UInt256 UI_LARGE_VALUE2 = fromBigInt(BI_LARGE_VALUE2);

    static UInt256 fromBigInt(BigInteger bi) {
    	return UInt256.from(bi.toByteArray());
    }

	@Benchmark
	public void addLargeLargeInt256(Blackhole bh) {
		bh.consume(UI_LARGE_VALUE1.add(UI_LARGE_VALUE2));
	}

	@Benchmark
	public void addLargeLargeBigInt(Blackhole bh) {
		bh.consume(BI_LARGE_VALUE1.add(BI_LARGE_VALUE2));
	}

	@Benchmark
	public void subLargeLargeInt256(Blackhole bh) {
		bh.consume(UI_LARGE_VALUE1.subtract(UI_LARGE_VALUE2));
	}

	@Benchmark
	public void subLargeLargeBigInt(Blackhole bh) {
		bh.consume(BI_LARGE_VALUE1.subtract(BI_LARGE_VALUE2));
	}

	@Benchmark
	public void mulLargeSmallInt256(Blackhole bh) {
		bh.consume(UI_LARGE_VALUE1.multiply(UI_SMALL_VALUE));
	}

	@Benchmark
	public void mulLargeSmallBigInt(Blackhole bh) {
		bh.consume(BI_LARGE_VALUE1.multiply(BI_SMALL_VALUE));
	}

	@Benchmark
	public void divLargeSmallInt256(Blackhole bh) {
		bh.consume(UI_LARGE_VALUE1.divide(UI_SMALL_VALUE));
	}

	@Benchmark
	public void divLargeSmallBigInt(Blackhole bh) {
		bh.consume(BI_LARGE_VALUE1.divide(BI_SMALL_VALUE));
	}

	@Benchmark
	public void sqrtLargeInt256(Blackhole bh) {
		bh.consume(UI_LARGE_VALUE1.isqrt());
	}

	@Benchmark
	public void sqrtLargeBigInt(Blackhole bh) {
		bh.consume(BigIntegerMath.sqrt(BI_LARGE_VALUE1, RoundingMode.FLOOR));
	}

}
