package com.radixdlt.tempo;

import com.radixdlt.tempo.reactive.TempoState;

/**
 * A handcrafted bundle of immutable {@link TempoState}s
 */
public interface TempoStateBundle {
	<T extends TempoState> T get(Class<T> stateClass);
}
