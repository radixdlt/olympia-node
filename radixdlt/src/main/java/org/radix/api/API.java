package org.radix.api;

import java.util.Collections;
import java.util.List;

import com.radixdlt.middleware2.converters.AtomToBinaryConverter;
import com.radixdlt.middleware2.processing.RadixEngineAtomProcessor;
import com.radixdlt.store.LedgerEntryStore;
import org.radix.RadixServer;
import org.radix.api.http.RadixHttpServer;
import org.radix.modules.Module;
import org.radix.modules.Modules;
import org.radix.modules.Service;
import org.radix.modules.exceptions.ModuleException;
import org.radix.modules.exceptions.ModuleStartException;
import org.radix.modules.exceptions.ModuleStopException;

public class API extends Service {

	private RadixHttpServer radixHttpServer;
	private LedgerEntryStore store;
	private RadixEngineAtomProcessor radixEngineAtomProcessor;
	private AtomToBinaryConverter atomToBinaryConverter;

	public API(LedgerEntryStore store, RadixEngineAtomProcessor radixEngineAtomProcessor, AtomToBinaryConverter atomToBinaryConverter) {
		super();
		this.store = store;
		this.radixEngineAtomProcessor = radixEngineAtomProcessor;
		this.atomToBinaryConverter = atomToBinaryConverter;
	}

	public RadixHttpServer getRadixHttpServer() {
		return radixHttpServer;
	}

	@Override
	public void start_impl() throws ModuleException {
		if (!Modules.isAvailable(RadixServer.class)) {
			throw new ModuleStartException("API is enabled but no RadixServer is available", this);
		}

		radixHttpServer = new RadixHttpServer(store, radixEngineAtomProcessor, atomToBinaryConverter);
		radixHttpServer.start();
	}

	@Override
	public void stop_impl() throws ModuleException {
		try {
			if (radixHttpServer != null) {
				radixHttpServer.stop();
			}
		} catch (Exception ex) {
			throw new ModuleStopException(ex, this);
		}
	}

	@Override
	public String getName() {
		return "API Handler";
	}

	@Override
	public List<Class<? extends Module>> getDependsOn() {
		return Collections.singletonList(RadixServer.class);
	}
}

