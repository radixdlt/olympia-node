package com.radixdlt.consensus;

import com.radixdlt.common.Atom;
import java.util.function.Consumer;

/**
 * Async callbacks from network proposal messages
 * TODO: change to an rx interface
 */
public interface NetworkRx {
	void addProposalCallback(Consumer<Atom> callback);
}
