package com.radixdlt.client.application.translate;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.ledger.ParticleStore;
import io.reactivex.Observable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ApplicationStore<R> {
	private final ParticleStore particleStore;
	private final ParticleReducer<R> reducer;
	private final ConcurrentHashMap<RadixAddress, Observable<R>> cache = new ConcurrentHashMap<>();

	public ApplicationStore(ParticleStore particleStore, ParticleReducer<R> reducer) {
		this.particleStore = particleStore;
		this.reducer = reducer;
	}

	public Observable<R> getState(RadixAddress address) {
		return cache.computeIfAbsent(address, addr ->
			particleStore.getParticles(address)
				.scanWith(reducer::initialState, reducer::reduce)
				.debounce(1000, TimeUnit.MILLISECONDS)
				.replay(1)
				.autoConnect()
		);
	}
}
