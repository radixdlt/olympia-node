package com.radixdlt.tempo.delivery;

import org.radix.properties.RuntimeProperties;

/**
 * Static configuration for a {@link SingleRequestDeliverer}
 */
public interface SingleRequestDelivererConfiguration {
	int requestQueueCapacity(int defaultValue);

	int requestProcessorThreads(int defaultValue);

	int requestTimeoutSeconds(int defaultValue);

	static SingleRequestDelivererConfiguration fromRuntimeProperties(RuntimeProperties properties) {
		return new SingleRequestDelivererConfiguration() {
			@Override
			public int requestQueueCapacity(int defaultValue) {
				return properties.get("tempo.delivery.request.request_queue_capacity", defaultValue);
			}

			@Override
			public int requestProcessorThreads(int defaultValue) {
				return properties.get("tempo.delivery.request.request_processor_threads", defaultValue);
			}

			@Override
			public int requestTimeoutSeconds(int defaultValue) {
				return properties.get("tempo.delivery.request.request_timeout_seconds", defaultValue);
			}
		};
	}
}
