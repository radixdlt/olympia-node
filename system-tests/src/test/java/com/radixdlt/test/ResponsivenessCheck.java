package com.radixdlt.test;

import io.reactivex.Observable;
import io.reactivex.Single;
import org.json.JSONObject;

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
		List<Observable<Single<JSONObject>>> individualChecks = network.getNodeNames().stream()
			.map(nodeName -> Observable.interval(checkInterval, checkIntervalUnit)
				.map(i -> network.fetchSystem(nodeName).timeout(timeoutInterval, timeoutIntervalUnit))
				.map(o -> o))
			.collect(Collectors.toList());
		return Observable.merge(individualChecks);
	}
}
