package org.radix.network2.messaging;

import java.util.Objects;

import org.radix.events.Events;
import org.radix.network2.TimeSupplier;
import org.radix.network2.transport.FirstMatchTransportManager;
import org.radix.properties.RuntimeProperties;
import org.radix.time.Time;

import com.google.inject.AbstractModule;
import com.radixdlt.serialization.Serialization;

/**
 * Guice configuration for {@link MessageCentral} that includes a UDP
 * transport.
 */
final class MessageCentralModule extends AbstractModule {

	private final MessageCentralConfiguration config;
	private final TimeSupplier timeSource;

	MessageCentralModule(RuntimeProperties properties) {
		this(MessageCentralConfiguration.fromRuntimeProperties(properties), Time::currentTimestamp);
	}

	MessageCentralModule(MessageCentralConfiguration config, TimeSupplier timeSource) {
		this.config = Objects.requireNonNull(config);
		this.timeSource = Objects.requireNonNull(timeSource);
	}

	@Override
	protected void configure() {
		// The main target
		bind(MessageCentral.class).to(MessageCentralImpl.class).asEagerSingleton();

		// MessageCentral dependencies
		bind(MessageCentralConfiguration.class).toInstance(this.config);
		bind(Serialization.class).toProvider(Serialization::getDefault);
		bind(TransportManager.class).to(FirstMatchTransportManager.class);
		bind(Events.class).toProvider(Events::getInstance);
		bind(TimeSupplier.class).toInstance(this.timeSource);
	}
}
