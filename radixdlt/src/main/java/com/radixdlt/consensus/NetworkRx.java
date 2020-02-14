package com.radixdlt.consensus;

import java.util.function.Consumer;

/**
 * Async callbacks from network proposal messages
 * TODO: change to an rx interface
 */
public interface NetworkRx {
	void addReceiveProposalCallback(Consumer<Vertex> callback);
	void addReceiveVoteCallback(Consumer<Vertex> callback);
}
