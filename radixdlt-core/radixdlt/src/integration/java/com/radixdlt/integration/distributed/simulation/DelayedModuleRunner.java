package com.radixdlt.integration.distributed.simulation;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.radixdlt.ModuleRunner;

/**
 * A wrapper for ModuleRunner that adds delay at startup.
 */
public final class DelayedModuleRunner implements ModuleRunner {

	private final ModuleRunner underlyingModuleRunner;
	private final long delayMillis;

	public DelayedModuleRunner(ModuleRunner underlyingRunner, long delayMillis) {
		this.underlyingModuleRunner = underlyingRunner;
		this.delayMillis = delayMillis;
	}

	@Override
	public void start() {
		Executors.newSingleThreadScheduledExecutor()
			.schedule(this.underlyingModuleRunner::start, this.delayMillis, TimeUnit.MILLISECONDS);
	}

	@Override
	public void stop() {
		underlyingModuleRunner.stop();
	}
}
