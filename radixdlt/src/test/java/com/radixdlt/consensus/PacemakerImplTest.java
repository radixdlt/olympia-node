package com.radixdlt.consensus;

import static org.assertj.core.api.Assertions.assertThat;

import io.reactivex.rxjava3.observers.TestObserver;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class PacemakerImplTest {
	@Test
	public void when_round_0_completes__then_current_round_should_be_1() throws Exception {
		PacemakerImpl pacemaker = new PacemakerImpl();
		TestObserver<Long> testObserver = TestObserver.create();
		pacemaker.localTimeouts().subscribe(testObserver);
		pacemaker.start();
		testObserver.await(PacemakerImpl.TIMEOUT_MILLISECONDS / 2, TimeUnit.MILLISECONDS);
		pacemaker.processQC(0L);
		assertThat(pacemaker.getCurrentRound()).isEqualTo(1L);
	}

	@Test
	public void when_round_0_completes__then_a_timeout_event_with_round_0_does_not_occur() throws Exception {
		PacemakerImpl pacemaker = new PacemakerImpl();
		TestObserver<Long> testObserver = TestObserver.create();
		pacemaker.localTimeouts().subscribe(testObserver);
		pacemaker.start();
		testObserver.await(PacemakerImpl.TIMEOUT_MILLISECONDS / 2, TimeUnit.MILLISECONDS);
		pacemaker.processQC(0L);
		testObserver.awaitCount(1);
		testObserver.assertValue(1L);
	}

	@Test
	public void when_timeout_occurs__then_only_a_single_timeout_event_occurs() throws Exception {
		PacemakerImpl pacemaker = new PacemakerImpl();
		TestObserver<Long> testObserver = TestObserver.create();
		pacemaker.localTimeouts().subscribe(testObserver);
		pacemaker.start();
		testObserver.assertEmpty();
		testObserver.awaitCount(1);
		testObserver.assertValue(0L);
		testObserver.await(PacemakerImpl.TIMEOUT_MILLISECONDS * 3, TimeUnit.MILLISECONDS);
		testObserver.assertValueCount(1);
		testObserver.assertNotComplete();
	}

	@Test
	public void when_2x_timeout_occurs__then_two_timeout_events_occur() {
		PacemakerImpl pacemaker = new PacemakerImpl();
		TestObserver<Long> testObserver = TestObserver.create();
		pacemaker.localTimeouts().subscribe(testObserver);
		pacemaker.start();
		testObserver.assertEmpty();
		testObserver.awaitCount(1);
		pacemaker.processLocalTimeout(0);
		testObserver.awaitCount(2);
		testObserver.assertValues(0L, 1L);
	}
}