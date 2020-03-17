/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.delivery;

import com.google.inject.Provides;
import com.radixdlt.properties.RuntimeProperties;

/**
 * Static configuration for a {@link LazyRequestDeliverer}
 */
public interface LazyRequestDelivererConfiguration {
	int requestQueueCapacity(int defaultValue);

	int requestProcessorThreads(int defaultValue);

	int requestTimeoutSeconds(int defaultValue);

	@Provides
	static LazyRequestDelivererConfiguration fromRuntimeProperties(RuntimeProperties properties) {
		return new LazyRequestDelivererConfiguration() {
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
