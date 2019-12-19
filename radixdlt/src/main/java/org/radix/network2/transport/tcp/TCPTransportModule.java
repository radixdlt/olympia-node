package org.radix.network2.transport.tcp;

import java.util.Objects;

import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.Transport;
import org.radix.network2.transport.TransportMetadata;
import org.radix.properties.RuntimeProperties;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

/**
 * Guice configuration for the TCP transport subsystem.
 */
public class TCPTransportModule extends AbstractModule {
	private final TCPConfiguration config;

	public TCPTransportModule(RuntimeProperties properties) {
		this(TCPConfiguration.fromRuntimeProperties(properties));
	}

	public TCPTransportModule(TCPConfiguration config) {
		this.config = Objects.requireNonNull(config);
	}

	@Override
	protected void configure() {
		// ConnectionManager dependencies
		Multibinder<Transport> transportMultibinder = Multibinder.newSetBinder(binder(), Transport.class);
		transportMultibinder.addBinding().to(NettyTCPTransport.class);

		// Main binding
		bind(NettyTCPTransport.class).to(NettyTCPTransportImpl.class);

		// NettyTCPTransportImpl dependencies
		bind(TCPConfiguration.class).toInstance(this.config);
		bind(TransportMetadata.class).annotatedWith(Names.named("local")).toInstance(StaticTransportMetadata.empty()); // Use defaults
		bind(TCPTransportOutboundConnectionFactory.class).toProvider(this::tcpTransportOutboundConnectionFactoryProvider);
		bind(TCPTransportControlFactory.class).toProvider(this::tcpTransportControlFactoryProvider);
	}

	private TCPTransportControlFactory tcpTransportControlFactoryProvider() {
		return TCPTransportControlImpl::new;
	}

	private TCPTransportOutboundConnectionFactory tcpTransportOutboundConnectionFactoryProvider() {
		return TCPTransportOutboundConnectionImpl::new;
	}
}
