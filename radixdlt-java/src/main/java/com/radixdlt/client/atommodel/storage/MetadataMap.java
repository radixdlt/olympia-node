package com.radixdlt.client.atommodel.storage;

import java.util.TreeMap;

/**
 * Distinct type for metadata maps, as these need to be serialized
 * and deserialized differently.
 */
public class MetadataMap extends TreeMap<String, String> {

	public MetadataMap() {
		// Nothing to do here
	}

}
