package com.radixdlt.client.application.translate;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.ledger.ParticleStore;
import io.reactivex.Observable;
import java.util.concurrent.ConcurrentHashMap;

public class ApplicationStore<T extends ApplicationState> {
	private final ParticleStore particleStore;
	private final ParticleReducer<T> reducer;
	private final ConcurrentHashMap<RadixAddress, Observable<T>> cache = new ConcurrentHashMap<>();

	public ApplicationStore(
		ParticleStore particleStore,
		ParticleReducer<T> reducer
	) {
		this.particleStore = particleStore;
		this.reducer = reducer;
	}

	private static class HeadableApplicationState<T extends ApplicationState> {
		private final T state;
		private final boolean isHead;

		HeadableApplicationState(T state, boolean isHead) {
			this.state = state;
			this.isHead = isHead;
		}

		boolean isHead() {
			return isHead;
		}

		T getState() {
			return state;
		}
	}

	public Observable<T> getState(RadixAddress address) {
		return cache.computeIfAbsent(address, addr ->
			particleStore.getParticles(address)
				.scanWith(
					() -> new HeadableApplicationState<>(reducer.initialState(), false),
					(s, p) -> {
						if (p.isHead()) {
							return new HeadableApplicationState<>(s.state, true);
						} else {
							return new HeadableApplicationState<>(reducer.reduce(s.state, p.getParticle()), false);
						}
					}
				)
				.filter(HeadableApplicationState::isHead)
				.map(HeadableApplicationState::getState)
		);
	}
}
