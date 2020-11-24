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

package com.radixdlt.consensus.epoch;

import com.google.common.util.concurrent.RateLimiter;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.liveness.PacemakerInfoSender;
import com.radixdlt.consensus.liveness.PacemakerTimeoutCalculator;
import com.radixdlt.consensus.liveness.PacemakerTimeoutSender;
import com.radixdlt.environment.EventDispatcher;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

/**
 * A view update sender implementation that also schedules a timeout message.
 */
public class EpochViewUpdateSenderWithTimeout implements EpochViewUpdateSender {

    private static final Logger log = LogManager.getLogger();

    private final RateLimiter logLimiter = RateLimiter.create(1.0);

    private final PacemakerTimeoutSender timeoutSender;
    private final PacemakerTimeoutCalculator timeoutCalculator;
    private final PacemakerInfoSender pacemakerInfoSender;
    private final EventDispatcher<EpochViewUpdate> viewUpdateDispatcher;

    public EpochViewUpdateSenderWithTimeout(
        PacemakerTimeoutSender timeoutSender,
        PacemakerTimeoutCalculator timeoutCalculator,
        PacemakerInfoSender pacemakerInfoSender,
        EventDispatcher<EpochViewUpdate> viewUpdateDispatcher
    ) {
        this.timeoutSender = Objects.requireNonNull(timeoutSender);
        this.timeoutCalculator = Objects.requireNonNull(timeoutCalculator);
        this.pacemakerInfoSender = Objects.requireNonNull(pacemakerInfoSender);
        this.viewUpdateDispatcher = Objects.requireNonNull(viewUpdateDispatcher);
    }

    @Override
    public void sendLocalViewUpdate(EpochViewUpdate epochViewUpdate) {
        final ViewUpdate viewUpdate = epochViewUpdate.getViewUpdate();
        long timeout = timeoutCalculator.timeout(viewUpdate.uncommittedViewsCount());

        Level logLevel = this.logLimiter.tryAcquire() ? Level.INFO : Level.TRACE;
        log.log(logLevel, "Sending view update: {} with timeout {}ms", viewUpdate, timeout);

        this.viewUpdateDispatcher.dispatch(epochViewUpdate);

        this.pacemakerInfoSender.sendCurrentView(viewUpdate.getCurrentView());
        this.timeoutSender.scheduleTimeout(viewUpdate.getCurrentView(), timeout);
    }
}
