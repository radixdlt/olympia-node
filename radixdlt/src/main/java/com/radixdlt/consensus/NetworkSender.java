package com.radixdlt.consensus;

/**
 * Interface for Event Coordinator to send things through a network
 */
public interface NetworkSender {
	void broadcastProposal(Vertex vertex);
	void broadcastTimeout(Timeout timeout);
	void sendVote(Vote vote);
}
