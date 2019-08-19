package org.radix.network2.transport;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.common.collect.ImmutableMap;

/**
 * Interface specifying metadata for specific transports.
 */
public interface TransportMetadata {

	@JsonCreator
	static TransportMetadata create(ImmutableMap<String, String> metadata) {
		return StaticTransportMetadata.from(metadata);
	}

	/**
	 * Retrieves the specified metadata property.
	 *
	 * @param property the name of the metadata property
	 * @return the property value, or {@code null} if no such property
	 */
	String get(String property);

}
