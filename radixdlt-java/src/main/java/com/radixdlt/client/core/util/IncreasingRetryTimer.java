package com.radixdlt.client.core.util;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.functions.Function;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.reactivex.functions.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncreasingRetryTimer implements Function<Observable<Throwable>, ObservableSource<Long>> {
	private static final Logger LOGGER = LoggerFactory.getLogger(IncreasingRetryTimer.class);

	private final Predicate<Throwable> filter;

	public IncreasingRetryTimer(Class<? extends Exception> retryOnClass) {
		Objects.requireNonNull(retryOnClass, "retryOnClass is required");

		this.filter = e -> retryOnClass.isAssignableFrom(e.getClass());
	}

	public IncreasingRetryTimer(Predicate<Throwable> filter) {
		Objects.requireNonNull(filter, "filter is required");

		this.filter = filter;
	}

	@Override
	public ObservableSource<Long> apply(Observable<Throwable> attempts) {
		return attempts
			.flatMap(a -> {
				if (filter.test(a)) {
					return Observable.just(a);
				} else {
					return Observable.error(a);
				}
			})
			.zipWith(Observable.range(1, 300), (n, i) -> i)
			.map(i -> Math.min(i * i, 100))
			.doOnNext(i -> LOGGER.info("Retrying in " + i + " seconds..."))
			.flatMap(i -> Observable.timer(i, TimeUnit.SECONDS));
	}
}
