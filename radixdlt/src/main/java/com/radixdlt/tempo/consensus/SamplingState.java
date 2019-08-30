package com.radixdlt.tempo.consensus;

import com.radixdlt.common.EUID;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SamplingState {
	private final Map<EUID, SingleSamplingState> states = new ConcurrentHashMap<>();

	public boolean add(EUID tag) {
		SingleSamplingState newState = new SingleSamplingState();
		return states.putIfAbsent(tag, newState) == newState;
	}

	public boolean contains(EUID tag) {
		return states.containsKey(tag);
	}

	private static final class SingleSamplingState {

	}
}
