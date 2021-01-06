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

package com.radixdlt.network.transport.tcp;

import java.util.Objects;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.Transport;
import com.radixdlt.network.transport.TransportMetadata;
import com.radixdlt.properties.RuntimeProperties;

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
	}

	@Provides
	private TCPTransportControlFactory tcpTransportControlFactoryProvider(SystemCounters counters) {
		return (conf, outboundFactory, transport) -> new TCPTransportControlImpl(conf, outboundFactory, transport, counters);
	}

	private TCPTransportOutboundConnectionFactory tcpTransportOutboundConnectionFactoryProvider() {
		return TCPTransportOutboundConnectionImpl::new;
	}
}
