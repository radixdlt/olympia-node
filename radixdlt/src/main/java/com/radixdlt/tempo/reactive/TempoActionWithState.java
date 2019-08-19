package com.radixdlt.tempo.reactive;

import com.radixdlt.tempo.TempoStateBundle;

public final class TempoActionWithState<T extends TempoAction> {
	private final T action;
	private final TempoStateBundle bundle;

	private TempoActionWithState(T action, TempoStateBundle bundle) {
		this.action = action;
		this.bundle = bundle;
	}

	public T getAction() {
		return action;
	}

	public TempoStateBundle getBundle() {
		return bundle;
	}

	public static <T extends TempoAction> TempoActionWithState from(T action, TempoStateBundle bundle) {
		return new TempoActionWithState(action, bundle);
	}
}
