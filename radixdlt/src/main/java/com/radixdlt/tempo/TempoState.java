package com.radixdlt.tempo;

/**
 * A marker interface for a flavour of state in Tempo
 */
public interface TempoState {
	// TODO hack to get json representation for debugging, revisit or remove later
	default Object getDebugRepresentation() {
		return "No representation defined";
	}
}
