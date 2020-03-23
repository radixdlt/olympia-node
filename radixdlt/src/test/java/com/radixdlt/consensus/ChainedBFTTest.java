package com.radixdlt.consensus;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import com.radixdlt.consensus.ChainedBFT.Event;
import com.radixdlt.consensus.liveness.PacemakerRx;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;

public class ChainedBFTTest {
	@Test
	public void when_events_get_emitted__then_event_coordinator_should_be_called() {
		EventCoordinatorNetworkRx networkRx = mock(EventCoordinatorNetworkRx.class);
		EventCoordinator eventCoordinator = mock(EventCoordinator.class);
		PacemakerRx pacemakerRx = mock(PacemakerRx.class);

		NewView newView = mock(NewView.class);
		when(networkRx.newViewMessages())
			.thenReturn(Observable.just(newView).concatWith(Observable.never()));

		Vertex proposal = mock(Vertex.class);
		when(networkRx.proposalMessages())
			.thenReturn(Observable.just(proposal).concatWith(Observable.never()));

		Vote vote = mock(Vote.class);
		when(networkRx.voteMessages())
			.thenReturn(Observable.just(vote).concatWith(Observable.never()));

		View view = mock(View.class);
		when(pacemakerRx.localTimeouts())
			.thenReturn(Observable.just(view).concatWith(Observable.never()));

		ChainedBFT chainedBFT = new ChainedBFT(
			eventCoordinator,
			networkRx,
			pacemakerRx
		);

		TestObserver<Event> testObserver = TestObserver.create();
		chainedBFT.processEvents().subscribe(testObserver);
		testObserver.awaitCount(4);
		testObserver.assertNotComplete();
		verify(eventCoordinator, times(1)).processRemoteNewView(eq(newView));
		verify(eventCoordinator, times(1)).processProposal(eq(proposal));
		verify(eventCoordinator, times(1)).processVote(eq(vote));
		verify(eventCoordinator, times(1)).processLocalTimeout(eq(view));
	}
}