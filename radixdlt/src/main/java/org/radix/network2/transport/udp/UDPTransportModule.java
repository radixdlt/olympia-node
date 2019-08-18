package org.radix.network2.transport.udp;

import java.util.Objects;

import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.Transport;
import org.radix.network2.transport.TransportMetadata;
import org.radix.properties.RuntimeProperties;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

/**
 * Guice configuration for the UDP transport subsystem.
 */
public class UDPTransportModule extends AbstractModule {
	private final RuntimeProperties properties;

	public UDPTransportModule(RuntimeProperties properties) {
		this.properties = Objects.requireNonNull(properties);
	}

	@Override
	protected void configure() {
		// UDPOnlyConnectionManager dependencies
	     Multibinder<Transport> transportMultibinder = Multibinder.newSetBinder(binder(), Transport.class);
	     transportMultibinder.addBinding().to(NettyUDPTransportImpl.class);

		// NettyUDPTransportImpl dependencies
		bind(UDPConfiguration.class).toInstance(UDPConfiguration.fromRuntimeProperties(properties));
		bind(TransportMetadata.class).annotatedWith(Names.named("local")).toInstance(StaticTransportMetadata.empty()); // Use defaults for now
		bind(UDPTransportControlFactory.class).toProvider(this::udpTransportControlFactoryProvider);
		bind(UDPTransportOutboundConnectionFactory.class).toProvider(this::udpTransportOutboundConnectionFactoryProvider);
		bind(PublicInetAddress.class).toProvider(PublicInetAddress::getInstance);
	}

	private UDPTransportControlFactory udpTransportControlFactoryProvider() {
		return UDPTransportControlImpl::new;
	}

	private UDPTransportOutboundConnectionFactory udpTransportOutboundConnectionFactoryProvider() {
		return UDPTransportOutboundConnection::new;
	}
}
