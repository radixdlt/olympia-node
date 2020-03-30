/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.core.util;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.functions.Function;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.reactivex.functions.Predicate;

public class IncreasingRetryTimer implements Function<Observable<Throwable>, ObservableSource<Long>> {
	private static final Logger LOGGER = LogManager.getLogger(IncreasingRetryTimer.class);

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
