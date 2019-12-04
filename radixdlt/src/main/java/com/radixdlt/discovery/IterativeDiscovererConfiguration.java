package com.radixdlt.discovery;

import org.radix.properties.RuntimeProperties;

/**
 * Static configuration for an {@link IterativeDiscoverer}
 */
public interface IterativeDiscovererConfiguration {
	int requestTimeoutSeconds(int defaultValue);

	int maxBackoff(int defaultValue);

	int responseLimit(int defaultValue);

	int requestQueueCapacity(int defaultValue);

	int requestProcessorThreads(int defaultValue);

	static IterativeDiscovererConfiguration fromRuntimeProperties(RuntimeProperties properties) {
		return new IterativeDiscovererConfiguration() {
			@Override
			public int requestTimeoutSeconds(int defaultValue) {
				return properties.get("tempo.discovery.iterative.request_timeout", defaultValue);
			}

			@Override
			public int maxBackoff(int defaultValue) {
				return properties.get("tempo.discovery.iterative.max_backoff", defaultValue);
			}

			@Override
			public int responseLimit(int defaultValue) {
				return properties.get("tempo.discovery.iterative.response_limit", defaultValue);
			}

			@Override
			public int requestQueueCapacity(int defaultValue) {
				return properties.get("tempo.discovery.iterative.request_queue_capacity", defaultValue);
			}

			@Override
			public int requestProcessorThreads(int defaultValue) {
				return properties.get("tempo.discovery.iterative.request_processor_threads", defaultValue);
			}
		};
	}
}
