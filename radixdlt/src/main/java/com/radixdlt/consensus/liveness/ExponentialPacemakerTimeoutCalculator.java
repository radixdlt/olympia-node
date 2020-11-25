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

package com.radixdlt.consensus.liveness;

import com.google.inject.Inject;
import com.radixdlt.consensus.bft.PacemakerMaxExponent;
import com.radixdlt.consensus.bft.PacemakerRate;
import com.radixdlt.consensus.bft.PacemakerTimeout;

/**
 * Timeout calculator which exponentially increases based on number of uncommitted views.
 */
public final class ExponentialPacemakerTimeoutCalculator implements PacemakerTimeoutCalculator {

    private final long timeoutMilliseconds;
    private final double rate;
    private final int maxExponent;

    @Inject
    public ExponentialPacemakerTimeoutCalculator(
        @PacemakerTimeout long timeoutMilliseconds,
        @PacemakerRate double rate,
        @PacemakerMaxExponent int maxExponent
    ) {
        if (timeoutMilliseconds <= 0) {
            throw new IllegalArgumentException("timeoutMilliseconds must be > 0 but was " + timeoutMilliseconds);
        }
        if (rate <= 1.0) {
            throw new IllegalArgumentException("rate must be > 1.0, but was " + rate);
        }
        if (maxExponent < 0) {
            throw new IllegalArgumentException("maxExponent must be >= 0, but was " + maxExponent);
        }
        double maxTimeout = timeoutMilliseconds * Math.pow(rate, maxExponent);
        if (maxTimeout > Long.MAX_VALUE) {
            throw new IllegalArgumentException("Maximum timeout value of " + maxTimeout + " is too large");
        }

        this.timeoutMilliseconds = timeoutMilliseconds;
        this.rate = rate;
        this.maxExponent = maxExponent;
    }

    @Override
    public long timeout(long uncommittedViews) {
        double exponential = Math.pow(this.rate, Math.min(this.maxExponent, uncommittedViews));
        return Math.round(this.timeoutMilliseconds * exponential);
    }
}
