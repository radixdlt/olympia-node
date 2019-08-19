package org.radix.network2.messaging;

import org.radix.properties.RuntimeProperties;

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
	int getMessagingInboundQueueMax(int defaultValue);

	/**
	 * Retrieves the maximum queue depth for outbound messages before
	 * further outgoing messages will be dropped.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return The maximum queue depth
	 */
	int getMessagingOutboundQueueMax(int defaultValue);

	/**
	 * Retrieves the maximum time-to-live for inbound and outbound messages in seconds.
	 * If messages are not processed and dispatched within this time, they will be
	 * dropped.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return Message time-to-live in seconds
	 */
	int getMessagingTimeToLive(int defaultValue);

	/**
	 * Create a configuration from specified {@link RuntimeProperties}.
	 *
	 * @param properties the properties to read the configuration from
	 * @return The configuration
	 */
	static MessageCentralConfiguration fromRuntimeProperties(RuntimeProperties properties) {
		return new MessageCentralConfiguration() {
			@Override
			public int getMessagingInboundQueueMax(int defaultValue) {
				return properties.get("messaging.inbound.queue_max", defaultValue);
			}

			@Override
			public int getMessagingOutboundQueueMax(int defaultValue) {
				return properties.get("messaging.outbound.queue_max", defaultValue);
			}

			@Override
			public int getMessagingTimeToLive(int defaultValue) {
				return properties.get("messaging.time_to_live", defaultValue);
			}
		};
	}
}
