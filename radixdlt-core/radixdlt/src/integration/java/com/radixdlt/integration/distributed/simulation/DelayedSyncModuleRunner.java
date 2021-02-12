package com.radixdlt.integration.distributed.simulation;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.radixdlt.ModuleRunner;
import com.radixdlt.SyncModuleRunner;

/**
 * A wrapper for SyncServiceRunner that adds delay at startup.
 */
public final class DelayedSyncModuleRunner implements SyncModuleRunner {

    private final ModuleRunner underlyingRunner;
    private final long delayMillis;

    public DelayedSyncModuleRunner(ModuleRunner underlyingRunner, long delayMillis) {
        this.underlyingRunner = underlyingRunner;
        this.delayMillis = delayMillis;
    }

    @Override
    public void start() {
        Executors.newSingleThreadScheduledExecutor()
            .schedule(this.underlyingRunner::start, this.delayMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        underlyingRunner.stop();
    }
}
