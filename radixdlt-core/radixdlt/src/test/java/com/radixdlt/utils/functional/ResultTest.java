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

import java.util.concurrent.atomic.AtomicBoolean;

import static com.radixdlt.utils.functional.Result.allOf;
import static com.radixdlt.utils.functional.Result.fail;
import static com.radixdlt.utils.functional.Result.ok;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ResultTest {
	static final Failure TEST_FAILURE = Failure.failure("Test error");

	@Test
	public void equalsFollowsContract() {
		assertEquals(ok("1"), ok("1"));
		assertNotEquals(ok(1), ok(2));
		assertNotEquals(ok(1), fail(TEST_FAILURE));
	}

	@Test
	public void orSelectsFirstSuccess() {
		assertEquals(ok(1), ok(1).or(ok(2)).or(ok(3)));
		assertEquals(ok(2), fail(TEST_FAILURE).or(ok(2)).or(ok(3)));
		assertEquals(ok(3), fail(TEST_FAILURE).or(fail(TEST_FAILURE)).or(ok(3)));
	}

	@Test
	public void ifSuccessCalledForSuccessResult() {
		final var result = new Integer[1];
		final var aBool = new AtomicBoolean(false);

		ok(5).onSuccess(v -> result[0] = v)
			.onSuccessDo(() -> aBool.set(true));

		assertEquals(5, (int) result[0]);
		assertTrue(aBool.get());
	}

	@Test
	public void ifFailureCalledForSuccessResult() {
		final var result = new Failure[1];
		final var aBool = new AtomicBoolean(false);

		fail(TEST_FAILURE).onFailure(v -> result[0] = v)
			.onFailureDo(() -> aBool.set(true));

		assertEquals(TEST_FAILURE, result[0]);
		assertTrue(aBool.get());
	}

	@Test
	public void testEquals() {
		assertNotEquals(ok(TEST_FAILURE), fail(TEST_FAILURE));
		assertEquals(ok(TEST_FAILURE), ok(TEST_FAILURE));
		assertEquals(fail(TEST_FAILURE), fail(TEST_FAILURE));
		assertEquals(ok(TEST_FAILURE).hashCode(), fail(TEST_FAILURE).hashCode());
	}

	@Test
	public void testApply() {
		ok(123).apply(f -> Assert.fail("Should not be invoked"), s -> assertEquals(123, (int) s));
		fail(TEST_FAILURE).apply(s -> assertEquals(TEST_FAILURE, s), s -> Assert.fail("Should not be invoked"));
	}

	@Test
	public void resultCanBeConvertedToOption() {
		ok(123).toOption()
			.whenEmpty(() -> Assert.fail("Should not be empty"))
			.whenPresent(v -> assertEquals(123, (int) v));
		fail(TEST_FAILURE).toOption()
			.whenPresent(v -> Assert.fail("Should not be empty"));
	}

	@Test
	public void oneResultsCanBeTransformed() {
		allOf(ok(1))
			.map(v1 -> {
				assertEquals(1, (int) v1);
				return true;
			})
			.onFailureDo(Assert::fail);
		allOf(ok(1))
			.flatMap(v1 -> {
				assertEquals(1, (int) v1);
				return Result.ok(v1);
			})
			.onFailureDo(Assert::fail)
			.onSuccess(v -> assertEquals(1, (int) v));
	}

	@Test
	public void twoResultsCanBeTransformed() {
		allOf(ok(1), ok(2))
			.map((v1, v2) -> {
				assertEquals(1, (int) v1);
				assertEquals(2, (int) v2);
				return true;
			})
			.onFailureDo(Assert::fail);
		allOf(ok(1), ok(2))
			.flatMap((v1, v2) -> {
				assertEquals(1, (int) v1);
				assertEquals(2, (int) v2);
				return Result.ok(v1 + v2);
			})
			.onFailureDo(Assert::fail)
			.onSuccess(v -> assertEquals(3, (int) v));
	}

	@Test
	public void threeResultsCanBeTransformed() {
		allOf(ok(1), ok(2), ok(3))
			.map((v1, v2, v3) -> {
				assertEquals(1, (int) v1);
				assertEquals(2, (int) v2);
				assertEquals(3, (int) v3);
				return true;
			})
			.onFailureDo(Assert::fail);
		allOf(ok(1), ok(2), ok(3))
			.flatMap((v1, v2, v3) -> {
				assertEquals(1, (int) v1);
				assertEquals(2, (int) v2);
				assertEquals(3, (int) v3);
				return Result.ok(v1 + v2 + v3);
			})
			.onFailureDo(Assert::fail)
			.onSuccess(v -> assertEquals(6, (int) v));
	}

	@Test
	public void fourResultsCanBeTransformed() {
		allOf(ok(1), ok(2), ok(3), ok(4))
			.map((v1, v2, v3, v4) -> {
				assertEquals(1, (int) v1);
				assertEquals(2, (int) v2);
				assertEquals(3, (int) v3);
				assertEquals(4, (int) v4);
				return true;
			})
			.onFailureDo(Assert::fail);
		allOf(ok(1), ok(2), ok(3), ok(4))
			.flatMap((v1, v2, v3, v4) -> {
				assertEquals(1, (int) v1);
				assertEquals(2, (int) v2);
				assertEquals(3, (int) v3);
				assertEquals(4, (int) v4);
				return Result.ok(v1 + v2 + v3 + v4);
			})
			.onFailureDo(Assert::fail)
			.onSuccess(v -> assertEquals(10, (int) v));
	}

	@Test
	public void fiveResultsCanBeTransformed() {
		allOf(ok(1), ok(2), ok(3), ok(4), ok(5))
			.map((v1, v2, v3, v4, v5) -> {
				assertEquals(1, (int) v1);
				assertEquals(2, (int) v2);
				assertEquals(3, (int) v3);
				assertEquals(4, (int) v4);
				assertEquals(5, (int) v5);
				return true;
			})
			.onFailureDo(Assert::fail);
		allOf(ok(1), ok(2), ok(3), ok(4), ok(5))
			.flatMap((v1, v2, v3, v4, v5) -> {
				assertEquals(1, (int) v1);
				assertEquals(2, (int) v2);
				assertEquals(3, (int) v3);
				assertEquals(4, (int) v4);
				assertEquals(5, (int) v5);
				return Result.ok(v1 + v2 + v3 + v4 + v5);
			})
			.onFailureDo(Assert::fail)
			.onSuccess(v -> assertEquals(15, (int) v));
	}

	@Test
	public void sixResultsCanBeTransformed() {
		allOf(ok(1), ok(2), ok(3), ok(4), ok(5), ok(6))
			.map((v1, v2, v3, v4, v5, v6) -> {
				assertEquals(1, (int) v1);
				assertEquals(2, (int) v2);
				assertEquals(3, (int) v3);
				assertEquals(4, (int) v4);
				assertEquals(5, (int) v5);
				assertEquals(6, (int) v6);
				return true;
			})
			.onFailureDo(Assert::fail);
		allOf(ok(1), ok(2), ok(3), ok(4), ok(5), ok(6))
			.flatMap((v1, v2, v3, v4, v5, v6) -> {
				assertEquals(1, (int) v1);
				assertEquals(2, (int) v2);
				assertEquals(3, (int) v3);
				assertEquals(4, (int) v4);
				assertEquals(5, (int) v5);
				assertEquals(6, (int) v6);
				return Result.ok(v1 + v2 + v3 + v4 + v5 + v6);
			})
			.onFailureDo(Assert::fail)
			.onSuccess(v -> assertEquals(21, (int) v));
	}

	@Test
	public void sevenResultsCanBeTransformed() {
		allOf(ok(1), ok(2), ok(3), ok(4), ok(5), ok(6), ok(7))
			.map((v1, v2, v3, v4, v5, v6, v7) -> {
				assertEquals(1, (int) v1);
				assertEquals(2, (int) v2);
				assertEquals(3, (int) v3);
				assertEquals(4, (int) v4);
				assertEquals(5, (int) v5);
				assertEquals(6, (int) v6);
				assertEquals(7, (int) v7);
				return true;
			})
			.onFailureDo(Assert::fail);
		allOf(ok(1), ok(2), ok(3), ok(4), ok(5), ok(6), ok(7))
			.flatMap((v1, v2, v3, v4, v5, v6, v7) -> {
				assertEquals(1, (int) v1);
				assertEquals(2, (int) v2);
				assertEquals(3, (int) v3);
				assertEquals(4, (int) v4);
				assertEquals(5, (int) v5);
				assertEquals(6, (int) v6);
				assertEquals(7, (int) v7);
				return Result.ok(v1 + v2 + v3 + v4 + v5 + v6 + v7);
			})
			.onFailureDo(Assert::fail)
			.onSuccess(v -> assertEquals(28, (int) v));
	}

	@Test
	public void eightResultsCanBeTransformed() {
		allOf(ok(1), ok(2), ok(3), ok(4), ok(5), ok(6), ok(7), ok(8))
			.map((v1, v2, v3, v4, v5, v6, v7, v8) -> {
				assertEquals(1, (int) v1);
				assertEquals(2, (int) v2);
				assertEquals(3, (int) v3);
				assertEquals(4, (int) v4);
				assertEquals(5, (int) v5);
				assertEquals(6, (int) v6);
				assertEquals(7, (int) v7);
				assertEquals(8, (int) v8);
				return true;
			})
			.onFailureDo(Assert::fail);
		allOf(ok(1), ok(2), ok(3), ok(4), ok(5), ok(6), ok(7), ok(8))
			.flatMap((v1, v2, v3, v4, v5, v6, v7, v8) -> {
				assertEquals(1, (int) v1);
				assertEquals(2, (int) v2);
				assertEquals(3, (int) v3);
				assertEquals(4, (int) v4);
				assertEquals(5, (int) v5);
				assertEquals(6, (int) v6);
				assertEquals(7, (int) v7);
				assertEquals(8, (int) v8);
				return Result.ok(v1 + v2 + v3 + v4 + v5 + v6 + v7 + v8);
			})
			.onFailureDo(Assert::fail)
			.onSuccess(v -> assertEquals(36, (int) v));
	}

	@Test
	public void nineResultsCanBeTransformed() {
		allOf(ok(1), ok(2), ok(3), ok(4), ok(5), ok(6), ok(7), ok(8), ok(9))
			.map((v1, v2, v3, v4, v5, v6, v7, v8, v9) -> {
				assertEquals(1, (int) v1);
				assertEquals(2, (int) v2);
				assertEquals(3, (int) v3);
				assertEquals(4, (int) v4);
				assertEquals(5, (int) v5);
				assertEquals(6, (int) v6);
				assertEquals(7, (int) v7);
				assertEquals(8, (int) v8);
				assertEquals(9, (int) v9);
				return true;
			})
			.onFailureDo(Assert::fail);
		allOf(ok(1), ok(2), ok(3), ok(4), ok(5), ok(6), ok(7), ok(8), ok(9))
			.flatMap((v1, v2, v3, v4, v5, v6, v7, v8, v9) -> {
				assertEquals(1, (int) v1);
				assertEquals(2, (int) v2);
				assertEquals(3, (int) v3);
				assertEquals(4, (int) v4);
				assertEquals(5, (int) v5);
				assertEquals(6, (int) v6);
				assertEquals(7, (int) v7);
				assertEquals(8, (int) v8);
				assertEquals(9, (int) v9);
				return Result.ok(v1 + v2 + v3 + v4 + v5 + v6 + v7 + v8 + v9);
			})
			.onFailureDo(Assert::fail)
			.onSuccess(v -> assertEquals(45, (int) v));
	}

	@Test
	public void inputFailureResultsToTransformationFailureForOneInput() {
		allOf(fail(TEST_FAILURE))
			.map((v1) -> true)
			.onFailure(f -> assertEquals(TEST_FAILURE, f))
			.onSuccessDo(Assert::fail);
	}

	@Test
	public void anyInputFailureResultsToTransformationFailureForTwoInputs() {
		allOf(ok(1), fail(TEST_FAILURE))
			.map((v1, v2) -> true)
			.onFailure(f -> assertEquals(TEST_FAILURE, f))
			.onSuccessDo(Assert::fail);
	}

	@Test
	public void anyInputFailureResultsToTransformationFailureForThreeInputs() {
		allOf(ok(1), ok(2), fail(TEST_FAILURE))
			.map((v1, v2, v3) -> true)
			.onFailure(f -> assertEquals(TEST_FAILURE, f))
			.onSuccessDo(Assert::fail);
	}

	@Test
	public void anyInputFailureResultsToTransformationFailureForFourInputs() {
		allOf(ok(1), ok(2), ok(3), fail(TEST_FAILURE))
			.map((v1, v2, v3, v4) -> true)
			.onFailure(f -> assertEquals(TEST_FAILURE, f))
			.onSuccessDo(Assert::fail);
	}

	@Test
	public void anyInputFailureResultsToTransformationFailureForFiveInputs() {
		allOf(ok(1), ok(2), ok(3), ok(4), fail(TEST_FAILURE))
			.map((v1, v2, v3, v4, v5) -> true)
			.onFailure(f -> assertEquals(TEST_FAILURE, f))
			.onSuccessDo(Assert::fail);
	}

	@Test
	public void anyInputFailureResultsToTransformationFailureForSixInputs() {
		allOf(ok(1), ok(2), ok(3), ok(4), ok(5), fail(TEST_FAILURE))
			.map((v1, v2, v3, v4, v5, v6) -> true)
			.onFailure(f -> assertEquals(TEST_FAILURE, f))
			.onSuccessDo(Assert::fail);
	}

	@Test
	public void anyInputFailureResultsToTransformationFailureForSevenInputs() {
		allOf(ok(1), ok(2), ok(3), ok(4), ok(5), ok(6), fail(TEST_FAILURE))
			.map((v1, v2, v3, v4, v5, v6, v7) -> true)
			.onFailure(f -> assertEquals(TEST_FAILURE, f))
			.onSuccessDo(Assert::fail);
	}

	@Test
	public void anyInputFailureResultsToTransformationFailureForEightInputs() {
		allOf(ok(1), ok(2), ok(3), ok(4), ok(5), ok(6), ok(7), fail(TEST_FAILURE))
			.map((v1, v2, v3, v4, v5, v6, v7, v8) -> true)
			.onFailure(f -> assertEquals(TEST_FAILURE, f))
			.onSuccessDo(Assert::fail);
	}

	@Test
	public void anyInputFailureResultsToTransformationFailureForNineInputs() {
		allOf(ok(1), ok(2), ok(3), ok(4), ok(5), ok(6), ok(7), ok(8), fail(TEST_FAILURE))
			.map((v1, v2, v3, v4, v5, v6, v7, v8, v9) -> true)
			.onFailure(f -> assertEquals(TEST_FAILURE, f))
			.onSuccessDo(Assert::fail);
	}
}
