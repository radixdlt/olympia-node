package com.radixdlt.client.util;

import com.radixdlt.client.core.util.IncreasingRetryTimer;
import io.reactivex.Observable;
import io.reactivex.functions.Predicate;
import io.reactivex.observers.TestObserver;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class IncreasingRetryTimerTest {
	@Test
	public void testNullConstruction() {
		Assertions.assertThatThrownBy(()
				-> new IncreasingRetryTimer((Predicate<Throwable>) null)).isInstanceOf(NullPointerException.class);
		Assertions.assertThatThrownBy(()
				-> new IncreasingRetryTimer((Class<? extends Exception>) null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	public void testFilterRetry() {
		IncreasingRetryTimer timer = new IncreasingRetryTimer(RuntimeException.class);
		TestObserver<Long> testObserver = TestObserver.create();
		timer.apply(Observable.just(new RuntimeException())).subscribe(testObserver);
		testObserver.assertSubscribed();
		testObserver.assertNoErrors();
	}

	@Test
	public void testFilterRethrow() {
		IncreasingRetryTimer timer = new IncreasingRetryTimer(RuntimeException.class);
		TestObserver<Long> testObserver = TestObserver.create();
		timer.apply(Observable.just(new Error())).subscribe(testObserver);
		testObserver.assertSubscribed();
		testObserver.assertError(Error.class);
	}
}