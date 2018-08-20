package com.radixdlt.client.application.actions;

import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An Application Layer Action object which stores bytes in an address.
 */
public class DataStore {
	private byte[] data;
	private Map<String, Object> metaData;
	private List<EncryptedPrivateKey> protectors;
	private List<RadixAddress> addresses = new ArrayList<>();

	public DataStore(Data data, RadixAddress address) {
		this.data = data.getBytes();
		this.metaData = data.getMetaData();
		this.protectors = data.getProtectors();

		addresses.add(address);
	}

	public DataStore(Data data, RadixAddress address0, RadixAddress address1) {
		this.data = data.getBytes();
		this.metaData = data.getMetaData();
		this.protectors = data.getProtectors();

		addresses.add(address0);
		addresses.add(address1);
	}

	public List<EncryptedPrivateKey> getProtectors() {
		return protectors;
	}

	// TODO: make this immutable
	public byte[] getData() {
		return data;
	}

	// TODO: make this immutable
	public Map<String, Object> getMetaData() {
		return metaData;
	}

	// TODO: make this immutable
	public List<RadixAddress> getAddresses() {
		return addresses;
	}
}
