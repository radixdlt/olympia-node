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

package com.radixdlt.network.messaging;

import com.radixdlt.properties.RuntimeProperties;

/**
 * Static configuration data for {@link MessageCentral}.
 */
public interface MessageCentralConfiguration {

	/**
	 * Retrieves the maximum queue depth for inbound messages before
	 * incoming messages will be dropped.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return The maximum queue depth
	 */
	int messagingInboundQueueMax(int defaultValue);

	/**
	 * Retrieves the maximum queue depth for outbound messages before
	 * further outgoing messages will be dropped.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return The maximum queue depth
	 */
	int messagingOutboundQueueMax(int defaultValue);

	/**
	 * Retrieves the maximum time-to-live for inbound and outbound messages in milliseconds.
	 * If messages are not processed and dispatched within this time, they will be
	 * dropped.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return Message time-to-live in milliseconds
	 */
	long messagingTimeToLive(long defaultValue);

	/**
	 * Create a configuration from specified {@link RuntimeProperties}.
	 *
	 * @param properties the properties to read the configuration from
	 * @return The configuration
	 */
	static MessageCentralConfiguration fromRuntimeProperties(RuntimeProperties properties) {
		return new MessageCentralConfiguration() {
			@Override
			public int messagingInboundQueueMax(int defaultValue) {
				return properties.get("messaging.inbound.queue_max", defaultValue);
			}

			@Override
			public int messagingOutboundQueueMax(int defaultValue) {
				return properties.get("messaging.outbound.queue_max", defaultValue);
			}

			@Override
			public long messagingTimeToLive(long defaultValue) {
				return properties.get("messaging.time_to_live", defaultValue);
			}
		};
	}
}
