package com.radixdlt.client.application.translate;

import com.radixdlt.client.application.actions.UniqueProperty;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.atoms.IdParticle;
import com.radixdlt.client.core.crypto.ECPublicKey;
import io.reactivex.Completable;
import io.reactivex.annotations.Nullable;
import java.util.Objects;

/**
 * Translates an application layer unique property object to an atom level object;
 */
public class UniquePropertyTranslator {
	public Completable translate(@Nullable UniqueProperty uniqueProperty, AtomBuilder atomBuilder) {
		Objects.requireNonNull(atomBuilder);

		if (uniqueProperty == null) {
			return Completable.complete();
		}

		ECPublicKey ecPublicKey = uniqueProperty.getAddress().getPublicKey();
		IdParticle particle = IdParticle.create("test", new EUID(uniqueProperty.getUnique()), ecPublicKey);
		atomBuilder.addParticle(particle);
		return Completable.complete();
	}
}
