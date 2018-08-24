package com.radixdlt.client.application.translate;

import com.radixdlt.client.application.actions.DataStore;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.atoms.DataParticle;
import com.radixdlt.client.core.atoms.Payload;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import io.reactivex.Completable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DataStoreTranslator {
	private static final DataStoreTranslator INSTANCE = new DataStoreTranslator();

	public static DataStoreTranslator getInstance() {
		return INSTANCE;
	}

	private DataStoreTranslator() {
	}

	public Completable translate(DataStore dataStore, AtomBuilder atomBuilder) {
		Payload payload = new Payload(dataStore.getData().getBytes());
		String application = (String) dataStore.getData().getMetaData().get("application");

		atomBuilder.setDataParticle(new DataParticle(payload, application));
		dataStore.getAddresses().forEach(atomBuilder::addDestination);

		return Completable.complete();
	}

	public Optional<Data> fromAtom(Atom atom) {
		if (atom.getDataParticle() == null) {
			return Optional.empty();
		}

		final List<EncryptedPrivateKey> protectors;
		if (atom.getEncryptor() != null && atom.getEncryptor().getProtectors() != null) {
			protectors = atom.getEncryptor().getProtectors();
		} else {
			protectors = Collections.emptyList();
		}

		Map<String, Object> metaData = new HashMap<>();
		metaData.put("timestamp", atom.getTimestamp());
		metaData.put("signatures", atom.getSignatures());
		metaData.put("application", atom.getDataParticle().getApplication());
		metaData.put("encrypted", !protectors.isEmpty());

		return Optional.of(Data.raw(atom.getDataParticle().getBytes().getBytes(), metaData, protectors));
	}
}
