package org.radix.network2.transport;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.common.collect.ImmutableMap;

public interface TransportMetadata {

	@JsonCreator
	static TransportMetadata create(ImmutableMap<String, String> metadata) {
		return StaticTransportMetadata.from(metadata);
	}

	String get(String property);

}
