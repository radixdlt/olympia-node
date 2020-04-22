package com.radixdlt.test;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ResponsivenessCheck implements RemoteBFTCheck {
	private final long checkInterval;
	private final TimeUnit checkIntervalUnit;
	private final long timeoutInterval;
	private final TimeUnit timeoutIntervalUnit;

	public ResponsivenessCheck(long checkInterval, TimeUnit checkIntervalUnit, long timeoutInterval, TimeUnit timeoutIntervalUnit) {
		if (checkInterval < 1) {
			throw new IllegalArgumentException("checkInterval must be >= 1 but was " + checkInterval);
		}
		this.checkInterval = checkInterval;
		this.checkIntervalUnit = Objects.requireNonNull(checkIntervalUnit);
		if (timeoutInterval < 1) {
			throw new IllegalArgumentException("timeoutInterval must be >= 1 but was " + timeoutInterval);
		}
		this.timeoutInterval = timeoutInterval;
		this.timeoutIntervalUnit = Objects.requireNonNull(timeoutIntervalUnit);
	}

	@Override
	public Observable<Object> check(DockerBFTTestNetwork network) {
		List<Observable<Disposable>> individualChecks = network.getNodeNames().stream()
			.map(nodeName -> Observable.interval(checkInterval, checkIntervalUnit)
				.doOnNext(i -> System.out.printf("ping %s", nodeName))
				.map(i -> network.queryJson(nodeName, "api/ping")
					.timeout(timeoutInterval, timeoutIntervalUnit)
					.subscribe(response -> System.out.printf("got pong from %s: %s", nodeName, response)))
				.map(o -> o))
			.collect(Collectors.toList());
		return Observable.merge(individualChecks);
	}
}
