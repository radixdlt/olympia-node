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

package org.radix.network2.transport.tcp;

import java.util.Objects;

import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.Transport;
import org.radix.network2.transport.TransportMetadata;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
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
		bind(TCPTransportControlFactory.class).toProvider(this::tcpTransportControlFactoryProvider);
	}

	private TCPTransportControlFactory tcpTransportControlFactoryProvider() {
		return TCPTransportControlImpl::new;
	}

	private TCPTransportOutboundConnectionFactory tcpTransportOutboundConnectionFactoryProvider() {
		return TCPTransportOutboundConnectionImpl::new;
	}
}
