package com.radixdlt.client.application.translate;

import com.radixdlt.client.application.actions.DataStore;
import com.radixdlt.client.core.atoms.ApplicationPayloadAtom;
import com.radixdlt.client.core.atoms.AtomBuilder;
import io.reactivex.Completable;

public class DataStoreTranslator {
	private static final DataStoreTranslator INSTANCE = new DataStoreTranslator();

	public static DataStoreTranslator getInstance() {
		return INSTANCE;
	}

	private DataStoreTranslator() {
	}

	public Completable translate(DataStore dataStore, AtomBuilder atomBuilder) {
		atomBuilder
			.type(ApplicationPayloadAtom.class)
			.protectors(dataStore.getProtectors())
			.payload(dataStore.getData());

		if (dataStore.getMetaData().containsKey("application")) {
			atomBuilder.applicationId((String) dataStore.getMetaData().get("application"));
		}

		dataStore.getAddresses().forEach(atomBuilder::addDestination);

		return Completable.complete();
	}
}
