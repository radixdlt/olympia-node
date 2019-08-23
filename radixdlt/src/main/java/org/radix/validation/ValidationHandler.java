package org.radix.validation;

import com.radixdlt.engine.RadixEngine;

import com.radixdlt.atomos.SimpleRadixEngineAtom;
import org.radix.modules.Service;

/**
 * Legacy validation handler to remain compatible with old usages in AtomSync and Conflict handlers until Dan's changes are merged
 */
public class ValidationHandler extends Service {
	private final RadixEngine<SimpleRadixEngineAtom> radixEngine;

	public ValidationHandler(RadixEngine<SimpleRadixEngineAtom> radixEngine) {
		this.radixEngine = radixEngine;
	}

	@Override
	public String getName() {
		return "Validation Handler";
	}

	public RadixEngine<SimpleRadixEngineAtom> getRadixEngine() {
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
