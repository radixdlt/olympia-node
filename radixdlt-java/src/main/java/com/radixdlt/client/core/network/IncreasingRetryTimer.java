package com.radixdlt.client.core.network;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.functions.Function;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncreasingRetryTimer implements Function<Observable<Throwable>, ObservableSource<Long>> {
	private static final Logger LOGGER = LoggerFactory.getLogger(IncreasingRetryTimer.class);

	@Override
	public ObservableSource<Long> apply(Observable<Throwable> attempts) {
		return attempts.doOnNext(t -> LOGGER.info(t.toString()))
			.zipWith(Observable.range(1, 300), (n, i) -> i)
			.map(i -> Math.min(i * i, 100))
			.doOnNext(i -> LOGGER.info("Retrying in " + i + " seconds..."))
			.flatMap(i -> Observable.timer(i, TimeUnit.SECONDS));
	}
}
