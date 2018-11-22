package com.radixdlt.client.application.translate.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.application.objects.UnencryptedData;
import com.radixdlt.client.application.translate.AtomToActionsMapper;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.atommodel.quarks.DataQuark;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import com.radixdlt.client.core.crypto.Encryptor;
import io.reactivex.Observable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AtomToMessageActionsMapper implements AtomToActionsMapper<UnencryptedData> {
	private static final JsonParser JSON_PARSER = new JsonParser();

	public Observable<UnencryptedData> map(Atom atom, RadixIdentity identity) {
		final Optional<MessageParticle> bytesParticle = atom.getDataParticles().stream()
			.filter(p -> !"encryptor".equals(p.getMetaData("application")))
			.findFirst();

		if (!bytesParticle.isPresent()) {
			return Observable.empty();
		}

		// TODO: don't pass in maps, utilize a metadata builder?
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("timestamp", atom.getTimestamp());
		metaData.put("signatures", atom.getSignatures());

		bytesParticle.ifPresent(p -> metaData.compute("application", (k, v) -> p.getMetaData("application")));

		final Optional<MessageParticle> encryptorParticle = atom.getDataParticles().stream()
			.filter(p -> "encryptor".equals(p.getMetaData("application")))
			.findAny();
		metaData.put("encrypted", encryptorParticle.isPresent());

		final Encryptor encryptor;
		if (encryptorParticle.isPresent()) {
			JsonArray protectorsJson = JSON_PARSER.parse(
				new String(encryptorParticle.get().getQuarkOrError(DataQuark.class).getBytes(),
					StandardCharsets.UTF_8)).getAsJsonArray();
			List<EncryptedPrivateKey> protectors = new ArrayList<>();
			protectorsJson.forEach(protectorJson -> protectors.add(EncryptedPrivateKey.fromBase64(protectorJson.getAsString())));
			encryptor = new Encryptor(protectors);
		} else {
			encryptor = null;
		}

		Data data = Data.raw(bytesParticle.get().getQuarkOrError(DataQuark.class).getBytes(), metaData, encryptor);

		return identity.decrypt(data).toMaybe().onErrorComplete().toObservable();
	}

}
