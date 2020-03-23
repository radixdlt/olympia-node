package com.radixdlt.consensus;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import com.radixdlt.consensus.ChainedBFT.Event;
import com.radixdlt.consensus.ChainedBFT.EventType;
import com.radixdlt.consensus.liveness.PacemakerRx;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.junit.Test;

public class ChainedBFTTest {
	@Test
	public void when_events_get_emitted__then_they_should_be_visible_by_observer() {
		EventCoordinatorNetworkRx networkRx = mock(EventCoordinatorNetworkRx.class);
		EventCoordinator eventCoordinator = mock(EventCoordinator.class);
		PacemakerRx pacemakerRx = mock(PacemakerRx.class);

		PublishSubject<NewView> newViewPublishSubject = PublishSubject.create();
		PublishSubject<Vertex> proposalPublishSubject = PublishSubject.create();
		PublishSubject<Vote> votePublishSubject = PublishSubject.create();

		when(networkRx.newViewMessages()).thenReturn(newViewPublishSubject);
		when(networkRx.proposalMessages()).thenReturn(proposalPublishSubject);
		when(networkRx.voteMessages()).thenReturn(votePublishSubject);

		PublishSubject<View> timeoutPublishSubject = PublishSubject.create();
		when(pacemakerRx.localTimeouts()).thenReturn(timeoutPublishSubject);

		ChainedBFT chainedBFT = new ChainedBFT(
			eventCoordinator,
			networkRx,
			pacemakerRx
		);

		TestObserver<Event> testObserver = TestObserver.create();
		chainedBFT.processEvents().subscribe(testObserver);

		NewView newView = mock(NewView.class);
		newViewPublishSubject.onNext(newView);
		testObserver.awaitCount(1);
		testObserver.assertValueAt(0, e -> e.getEventType() == EventType.NEW_VIEW_MESSAGE);
		verify(eventCoordinator, times(1)).processRemoteNewView(eq(newView));

		Vertex proposal = mock(Vertex.class);
		proposalPublishSubject.onNext(proposal);
		testObserver.awaitCount(2);
		testObserver.assertValueAt(1, e -> e.getEventType() == EventType.PROPOSAL_MESSAGE);
		verify(eventCoordinator, times(1)).processProposal(eq(proposal));

		Vote vote = mock(Vote.class);
		votePublishSubject.onNext(vote);
		testObserver.awaitCount(3);
		testObserver.assertValueAt(2, e -> e.getEventType() == EventType.VOTE_MESSAGE);
		verify(eventCoordinator, times(1)).processVote(eq(vote));

		View view = mock(View.class);
		timeoutPublishSubject.onNext(view);
		testObserver.awaitCount(4);
		testObserver.assertValueAt(3, e -> e.getEventType() == EventType.LOCAL_TIMEOUT);
		verify(eventCoordinator, times(1)).processLocalTimeout(eq(view));
	}
}