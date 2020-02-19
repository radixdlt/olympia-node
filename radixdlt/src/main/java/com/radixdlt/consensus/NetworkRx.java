package com.radixdlt.consensus;

import java.util.function.Consumer;

/**
 * Async callbacks from network proposal messages
 * TODO: change to an rx interface
 */
public interface NetworkRx {

	/**
	 * Throw away callback until rx is implemented
	 */
	void addReceiveProposalCallback(Consumer<Vertex> callback);

	/**
	 * Throw away callback until rx is implemented
	 */
	void addReceiveNewViewCallback(Consumer<NewView> callback);

	/**
	 * Throw away callback until rx is implemented
	 */
	void addReceiveVoteCallback(Consumer<Vote> callback);
}
