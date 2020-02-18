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
	private final AtomicReference<Consumer<Timeout>> timeoutCallbackRef;
	private final AtomicReference<Consumer<Vote>> voteCallbackRef;
	private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

	public DumbNetwork() {
		this.proposalCallbackRef = new AtomicReference<>();
		this.timeoutCallbackRef = new AtomicReference<>();
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
	public void broadcastTimeout(Timeout timeout) {
		executorService.schedule(() -> {
			Consumer<Timeout> callback = this.timeoutCallbackRef.get();
			if (callback != null) {
				callback.accept(timeout);
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
	public void addReceiveTimeoutCallback(Consumer<Timeout> callback) {
		this.timeoutCallbackRef.set(callback);
	}

	@Override
	public void addReceiveVoteCallback(Consumer<Vote> callback) {
		this.voteCallbackRef.set(callback);
	}
}
