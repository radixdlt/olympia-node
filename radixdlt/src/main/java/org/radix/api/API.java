package org.radix.api;

import com.radixdlt.middleware2.converters.AtomToBinaryConverter;
import com.radixdlt.middleware2.processing.RadixEngineAtomProcessor;
import com.radixdlt.store.LedgerEntryStore;
import org.radix.api.http.RadixHttpServer;

public class API {

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

	public void start() {
		radixHttpServer = new RadixHttpServer(store, radixEngineAtomProcessor, atomToBinaryConverter);
		radixHttpServer.start();
	}

	public void stop() {
		if (radixHttpServer != null) {
			radixHttpServer.stop();
		}
	}
}

