package com.radixdlt.consensus;

import io.reactivex.rxjava3.observers.TestObserver;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class PacemakerImplTest {
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