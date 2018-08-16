package com.radixdlt.client.application;

import com.radixdlt.client.core.atoms.ApplicationPayloadAtom;
import com.radixdlt.client.core.identity.RadixIdentity;
import io.reactivex.Maybe;
import java.util.HashMap;
import java.util.Map;

public class UnencryptedData {
	private final Map<String, Object> metaData;
	private final byte[] data;

	public UnencryptedData(byte[] data, Map<String, Object> metaData) {
		this.data = data;
		this.metaData = metaData;
	}

	public Map<String, Object> getMetaData() {
		return metaData;
	}

	public byte[] getData() {
		return data;
	}

	public static Maybe<UnencryptedData> fromAtom(ApplicationPayloadAtom atom, RadixIdentity identity) {
		if (atom.getEncryptor() != null && atom.getEncryptor().getProtectors() != null) {
			EncryptedData encryptedData = EncryptedData.fromAtom(atom);
			return identity.decrypt(encryptedData)
				.map(data -> new UnencryptedData(data, encryptedData.getMetaData()))
				.toMaybe().onErrorComplete();
		} else {
			Map<String, Object> metaData = new HashMap<>();
			metaData.put("timestamp", atom.getTimestamp());
			metaData.put("signatures", atom.getSignatures());
			metaData.put("application", atom.getApplicationId());
			return Maybe.just(new UnencryptedData(atom.getPayload().getBytes(), metaData));
		}
	}
}
