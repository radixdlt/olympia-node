package org.radix.validation;

import com.radixdlt.engine.RadixEngine;

import org.radix.modules.Service;

/**
 * Legacy validation handler to remain compatible with old usages in AtomSync and Conflict handlers until Dan's changes are merged
 */
public class ValidationHandler extends Service {
	private final RadixEngine radixEngine;

	public ValidationHandler(RadixEngine radixEngine) {
		this.radixEngine = radixEngine;
	}

	@Override
	public String getName() {
		return "Validation Handler";
	}

	public RadixEngine getRadixEngine() {
		return radixEngine;
	}

	@Override
	public void start_impl() {
		radixEngine.start();
	}

	@Override
	public void stop_impl() {
	}
}
