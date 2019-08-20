package org.radix.network2.messaging;

import java.util.Objects;

import org.radix.events.Events;
import org.radix.network2.transport.FirstMatchTransportManager;
import org.radix.properties.RuntimeProperties;

import com.google.inject.AbstractModule;
import com.radixdlt.serialization.Serialization;

/**
 * Guice configuration for {@link MessageCentral} that includes a UDP
 * transport.
 */
final class MessageCentralModule extends AbstractModule {

	private final RuntimeProperties properties;

	MessageCentralModule(RuntimeProperties properties) {
		this.properties = Objects.requireNonNull(properties);
	}

	@Override
	protected void configure() {
		// The main target
		bind(MessageCentral.class).to(MessageCentralImpl.class);

		// MessageCentral dependencies
		bind(MessageCentralConfiguration.class).toInstance(MessageCentralConfiguration.fromRuntimeProperties(properties));
		bind(Serialization.class).toProvider(Serialization::getDefault);
		bind(TransportManager.class).to(FirstMatchTransportManager.class);
		bind(Events.class).toProvider(Events::getInstance);
	}
}