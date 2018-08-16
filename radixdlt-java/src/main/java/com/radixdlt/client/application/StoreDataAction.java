package com.radixdlt.client.application;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.ApplicationPayloadAtom;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An Application Layer Action object which stores data in an address.
 */
public class StoreDataAction {
	private byte[] data;
	private Map<String, Object> metaData;
	private List<EncryptedPrivateKey> protectors;
	private List<RadixAddress> addresses = new ArrayList<>();

	public StoreDataAction(EncryptedData encryptedData, RadixAddress address) {
		this.data = encryptedData.getEncrypted();
		this.metaData = encryptedData.getMetaData();
		this.protectors = encryptedData.getProtectors();

		addresses.add(address);
	}

	public StoreDataAction(EncryptedData encryptedData, RadixAddress address0, RadixAddress address1) {
		this.data = encryptedData.getEncrypted();
		this.metaData = encryptedData.getMetaData();
		this.protectors = encryptedData.getProtectors();

		addresses.add(address0);
		addresses.add(address1);
	}

	// TODO: move this to a separate module
	public void addToAtomBuilder(AtomBuilder atomBuilder) {
		atomBuilder
			.type(ApplicationPayloadAtom.class)
			.protectors(protectors)
			.payload(data);

		if (metaData.containsKey("application")) {
			atomBuilder.applicationId((String) metaData.get("application"));
		}

		addresses.forEach(atomBuilder::addDestination);
	}
}
