package org.radix.network2.transport;

public interface Transport {

	String getName();

	TransportControl control();

	int maxMessageSize();
	int connectionLatency();

	TransportMetadata metadata();

}
