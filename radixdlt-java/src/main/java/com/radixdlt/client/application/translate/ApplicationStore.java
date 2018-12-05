package com.radixdlt.client.application.translate;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.ledger.ParticleStore;
import io.reactivex.Observable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ApplicationStore<T extends ApplicationState> {
	private final ParticleStore particleStore;
	private final ParticleReducer<T> reducer;
	private final Class<T> storeClass;
	private final ConcurrentHashMap<RadixAddress, Observable<T>> cache = new ConcurrentHashMap<>();

	public ApplicationStore(
		ParticleStore particleStore,
		ParticleReducer<T> reducer,
		Class<T> storeClass
	) {
		this.particleStore = particleStore;
		this.reducer = reducer;
		this.storeClass = storeClass;
	}

	public Class<T> storeClass() {
		return storeClass;
	}

	public Observable<T> getState(RadixAddress address) {
		return cache.computeIfAbsent(address, addr ->
			particleStore.getParticles(address)
				.scanWith(reducer::initialState, reducer::reduce)
				.debounce(1000, TimeUnit.MILLISECONDS)
				.replay(1)
				.autoConnect()
		);
	}
}
