package com.radixdlt.test;

import io.reactivex.Observable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class RemoteBFTTest {
	private final DockerBFTTestNetwork testNetwork;
	private final List<RemoteBFTCheck> checks = new ArrayList<>();

	public RemoteBFTTest(DockerBFTTestNetwork testNetwork) {
		this.testNetwork = Objects.requireNonNull(testNetwork);
	}

	public void run(long runtime, TimeUnit runtimeUnit) {
		List<Observable<Object>> assertions = checks.stream().map(check -> check.check(testNetwork)).collect(Collectors.toList());
		Observable.merge(assertions)
			.take(runtime, runtimeUnit)
			.blockingSubscribe();

		// perform actual assertions based on the responses we get to periodic api requests
		// TODO fetch consensus data from each node and check every refreshInterval until runtimeSeconds is over
		// see LatentNetwork integration test in core
//		Observable<Map<String, Map<String, Integer>>> consensusCountersPerNode = testNetwork.getConsensusEventCounters();
//		consensusCountersPerNode
//			.doOnNext(allCountersPerNode -> allCountersPerNode.forEach((node, counters) -> System.out.println(String.format("%s: %s", node, counters.toString()))));
//		// TODO assert that the views are all good (how?)
	}

	public DockerBFTTestNetwork getTestNetwork() {
		return testNetwork;
	}
}
