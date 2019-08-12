package com.radixdlt.tempo;

public interface TempoStateBundle {
	<T extends TempoState> T get(Class<T> state);
}
