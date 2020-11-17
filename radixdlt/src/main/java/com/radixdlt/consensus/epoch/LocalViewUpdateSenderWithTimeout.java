package com.radixdlt.consensus.epoch;

import com.google.common.util.concurrent.RateLimiter;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.liveness.PacemakerInfoSender;
import com.radixdlt.consensus.liveness.PacemakerTimeoutCalculator;
import com.radixdlt.consensus.liveness.PacemakerTimeoutSender;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A view update sender implementation that also schedules a timeout message.
 */
public class LocalViewUpdateSenderWithTimeout implements LocalViewUpdateSender {

    private static final Logger log = LogManager.getLogger();

    private final RateLimiter logLimiter = RateLimiter.create(1.0);

    private final PacemakerTimeoutSender timeoutSender;
    private final PacemakerTimeoutCalculator timeoutCalculator;
    private final PacemakerInfoSender pacemakerInfoSender;
    private final Consumer<LocalViewUpdate> sendFn;

    public LocalViewUpdateSenderWithTimeout(
        PacemakerTimeoutSender timeoutSender,
        PacemakerTimeoutCalculator timeoutCalculator,
        PacemakerInfoSender pacemakerInfoSender,
        Consumer<LocalViewUpdate> sendFn
    ) {
        this.timeoutSender = Objects.requireNonNull(timeoutSender);
        this.timeoutCalculator = Objects.requireNonNull(timeoutCalculator);
        this.pacemakerInfoSender = Objects.requireNonNull(pacemakerInfoSender);
        this.sendFn = sendFn;
    }

    @Override
    public void sendLocalViewUpdate(LocalViewUpdate localViewUpdate) {
        final ViewUpdate viewUpdate = localViewUpdate.getViewUpdate();

        long timeout = timeoutCalculator.timeout(viewUpdate.uncommittedViewsCount());

        Level logLevel = this.logLimiter.tryAcquire() ? Level.INFO : Level.TRACE;
        log.log(logLevel, "Sending view update: {} with timeout {}ms", viewUpdate, timeout);

        sendFn.accept(localViewUpdate);
        this.pacemakerInfoSender.sendCurrentView(viewUpdate.getCurrentView());
        this.timeoutSender.scheduleTimeout(viewUpdate.getCurrentView(), timeout);
    }
}
