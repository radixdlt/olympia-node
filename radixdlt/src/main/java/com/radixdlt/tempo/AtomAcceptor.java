package com.radixdlt.tempo;

import java.util.function.Consumer;

/**
 * Accepts atoms once that have been accepted in some form.
 */
public interface AtomAcceptor extends Consumer<TempoAtom> {
	// only extends consumer interface
}
