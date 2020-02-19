package com.radixdlt.consensus;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Overly simplistic network implementation that just sends messages to itself.
 */
public class DumbNetwork implements NetworkSender, NetworkRx {
	public static final int LOOPBACK_DELAY = 100;
	private final AtomicReference<Consumer<Vertex>> proposalCallbackRef;
	private final AtomicReference<Consumer<NewRound>> newRoundCallbackRef;
	private final AtomicReference<Consumer<Vote>> voteCallbackRef;
	private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

	public DumbNetwork() {
		this.proposalCallbackRef = new AtomicReference<>();
		this.newRoundCallbackRef = new AtomicReference<>();
		this.voteCallbackRef = new AtomicReference<>();
	}

	@Override
	public void broadcastProposal(Vertex vertex) {
		executorService.schedule(() -> {
			Consumer<Vertex> callback = this.proposalCallbackRef.get();
			if (callback != null) {
				callback.accept(vertex);
			}
		}, LOOPBACK_DELAY, TimeUnit.MILLISECONDS);
	}

	@Override
	public void sendNewView(NewRound newRound) {
		executorService.schedule(() -> {
			Consumer<NewRound> callback = this.newRoundCallbackRef.get();
			if (callback != null) {
				callback.accept(newRound);
			}
		}, LOOPBACK_DELAY, TimeUnit.MILLISECONDS);
	}

	@Override
	public void sendVote(Vote vote) {
		executorService.schedule(() -> {
			Consumer<Vote> callback = this.voteCallbackRef.get();
			if (callback != null) {
				callback.accept(vote);
			}
		}, LOOPBACK_DELAY, TimeUnit.MILLISECONDS);
	}

	@Override
	public void addReceiveProposalCallback(Consumer<Vertex> callback) {
		this.proposalCallbackRef.set(callback);
	}

	@Override
	public void addReceiveNewRoundCallback(Consumer<NewRound> callback) {
		this.newRoundCallbackRef.set(callback);
	}

	@Override
	public void addReceiveVoteCallback(Consumer<Vote> callback) {
		this.voteCallbackRef.set(callback);
	}
}
