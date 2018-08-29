package com.radixdlt.client.application.translate;

import com.radixdlt.client.application.actions.DataStore;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.atoms.DataParticle;
import com.radixdlt.client.core.atoms.EncryptorParticle;
import com.radixdlt.client.core.atoms.Payload;
import com.radixdlt.client.core.crypto.Encryptor;
import io.reactivex.Completable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class DataStoreTranslator {
	private static final DataStoreTranslator INSTANCE = new DataStoreTranslator();

	public static DataStoreTranslator getInstance() {
		return INSTANCE;
	}

	private DataStoreTranslator() {
	}

	// TODO: figure out correct method signature here (return Single<AtomBuilder> instead?)
	public Completable translate(DataStore dataStore, AtomBuilder atomBuilder) {
		Payload payload = new Payload(dataStore.getData().getBytes());
		String application = (String) dataStore.getData().getMetaData().get("application");

		atomBuilder.setDataParticle(new DataParticle(payload, application));
		Encryptor encryptor = dataStore.getData().getEncryptor();
		if (encryptor != null) {
			atomBuilder.setEncryptorParticle(new EncryptorParticle(encryptor.getProtectors()));
		}
		dataStore.getAddresses().forEach(atomBuilder::addDestination);

		return Completable.complete();
	}

	public Optional<Data> fromAtom(Atom atom) {
		if (atom.getDataParticle() == null) {
			return Optional.empty();
		}

		// TODO: don't pass in maps, utilize a metadata builder?
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("timestamp", atom.getTimestamp());
		metaData.put("signatures", atom.getSignatures());
		metaData.compute("application", (k, v) -> atom.getDataParticle().getMetaData("application"));
		metaData.put("encrypted", atom.getEncryptor() != null);

		final Encryptor encryptor;
		if (atom.getEncryptor() != null) {
			encryptor = new Encryptor(atom.getEncryptor().getProtectors());
		} else {
			encryptor = null;
		}

		return Optional.of(Data.raw(atom.getDataParticle().getBytes().getBytes(), metaData, encryptor));
	}
}
