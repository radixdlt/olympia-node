package com.radixdlt.client.application.translate;

import com.radixdlt.client.application.actions.StoreDataAction;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.core.atoms.ApplicationPayloadAtom;
import com.radixdlt.client.core.atoms.AtomBuilder;
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

	public Completable translate(StoreDataAction storeDataAction, AtomBuilder atomBuilder) {
		atomBuilder.type(ApplicationPayloadAtom.class);
		atomBuilder.payload(storeDataAction.getData().getBytes());

		if (!storeDataAction.getData().getProtectors().isEmpty()) {
			atomBuilder.protectors(storeDataAction.getData().getProtectors());
		}

		if (storeDataAction.getData().getMetaData().containsKey("application")) {
			atomBuilder.applicationId((String) storeDataAction.getData().getMetaData().get("application"));
		}

		storeDataAction.getAddresses().forEach(atomBuilder::addDestination);

		return Completable.complete();
	}

	public Data fromAtom(ApplicationPayloadAtom atom) {
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
