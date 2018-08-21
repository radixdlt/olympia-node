package com.radixdlt.client.application.translate;

import com.radixdlt.client.application.actions.DataStore;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.atoms.PayloadAtom;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import io.reactivex.Completable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataStoreTranslator {
	private static final DataStoreTranslator INSTANCE = new DataStoreTranslator();

	public static DataStoreTranslator getInstance() {
		return INSTANCE;
	}

	private DataStoreTranslator() {
	}

	public Completable translate(DataStore dataStore, AtomBuilder atomBuilder) {
		atomBuilder.type(PayloadAtom.class);
		atomBuilder.payload(dataStore.getData().getBytes());

		if (!dataStore.getData().getProtectors().isEmpty()) {
			atomBuilder.protectors(dataStore.getData().getProtectors());
		}

		if (dataStore.getData().getMetaData().containsKey("application")) {
			atomBuilder.applicationId((String) dataStore.getData().getMetaData().get("application"));
		}

		dataStore.getAddresses().forEach(atomBuilder::addDestination);

		return Completable.complete();
	}

	public Data fromAtom(PayloadAtom atom) {
		final List<EncryptedPrivateKey> protectors;
		if (atom.getEncryptor() != null && atom.getEncryptor().getProtectors() != null) {
			protectors = atom.getEncryptor().getProtectors();
		} else {
			protectors = Collections.emptyList();
		}

		Map<String, Object> metaData = new HashMap<>();
		metaData.put("timestamp", atom.getTimestamp());
		metaData.put("signatures", atom.getSignatures());
		metaData.put("application", atom.getApplicationId());
		metaData.put("encrypted", !protectors.isEmpty());

		return Data.raw(atom.getPayload().getBytes(), metaData, protectors);
	}
}
