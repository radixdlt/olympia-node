package com.radixdlt.client.application.actions;

import com.radixdlt.client.application.objects.EncryptedData;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An Application Layer Action object which stores data in an address.
 */
public class DataStore {
	private byte[] data;
	private Map<String, Object> metaData;
	private List<EncryptedPrivateKey> protectors;
	private List<RadixAddress> addresses = new ArrayList<>();

	public DataStore(EncryptedData encryptedData, RadixAddress address) {
		this.data = encryptedData.getEncrypted();
		this.metaData = encryptedData.getMetaData();
		this.protectors = encryptedData.getProtectors();

		addresses.add(address);
	}

	public DataStore(EncryptedData encryptedData, RadixAddress address0, RadixAddress address1) {
		this.data = encryptedData.getEncrypted();
		this.metaData = encryptedData.getMetaData();
		this.protectors = encryptedData.getProtectors();

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

	public Map<String, Object> getMetaData() {
		return metaData;
	}

	public List<RadixAddress> getAddresses() {
		return addresses;
	}
}
