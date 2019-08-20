package org.radix.network2.messaging;

import org.radix.network2.transport.udp.UDPTransportModule;
import org.radix.properties.RuntimeProperties;

import com.google.inject.Guice;
import com.google.inject.Injector;

/**
 * Factory for creating a {@link MessageCentral}.
 */
public class MessageCentralFactory {

	/**
	 * Create a {@link MessageCentral} based on a default configuration.
	 * Note that the default configuration is unspecified right now, but
	 * at least will include sufficient transports that nodes will be able
	 * to talk to each other if they use it.
	 *
	 * @param properties Static configuration properties to use when creating
	 * @return The newly constructed {@link MessageCentral}
	 */
	public MessageCentral getDefault(RuntimeProperties properties) {
		Injector injector = Guice.createInjector(
			new MessageCentralModule(properties),
			new UDPTransportModule(properties)
		);
		return injector.getInstance(MessageCentral.class);
	}

}
