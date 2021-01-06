/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.liveness;

import org.junit.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;

public class ExponentialPacemakerTimeoutCalculatorTest {

    @Test
    public void when_creating_timeout_calculator_with_invalid_timeout__then_exception_is_thrown() {
        checkConstructionParams(0, 1.2, 1, "timeoutMilliseconds must be > 0");
        checkConstructionParams(-1, 1.2, 1, "timeoutMilliseconds must be > 0");
        checkConstructionParams(1, 1.0, 1, "rate must be > 1.0");
        checkConstructionParams(1, 1.2, -1, "maxExponent must be >= 0");
        checkConstructionParams(1, 100.0, 100, "Maximum timeout value");
    }

    @Test
    public void timeout_should_grow_exponentially() {
        final ExponentialPacemakerTimeoutCalculator calculator =
            new ExponentialPacemakerTimeoutCalculator(1000L, 2.0, 6);

        final Map<Long, Long> expectedTimeouts = Map.of(
            0L, 1000L,
            1L, 2000L,
            2L, 4000L,
            3L, 8000L,
            4L, 16000L,
            5L, 32000L
        );

        expectedTimeouts.forEach((uncommittedViews, expectedResult) ->
            assertEquals(expectedResult.longValue(), calculator.timeout(uncommittedViews))
        );
    }

    private void checkConstructionParams(long timeout, double rate, int maxExponent, String exceptionMessage) {
        assertThatThrownBy(() -> new ExponentialPacemakerTimeoutCalculator(timeout, rate, maxExponent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith(exceptionMessage);
    }
}
