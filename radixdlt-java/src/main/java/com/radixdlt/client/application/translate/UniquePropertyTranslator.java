package com.radixdlt.client.application.translate;

import com.radixdlt.client.application.actions.UniqueProperty;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.atoms.Payload;
import com.radixdlt.client.core.atoms.particles.UniqueParticle;
import com.radixdlt.client.core.crypto.ECPublicKey;
import io.reactivex.annotations.Nullable;
import java.util.Objects;

/**
 * Translates an application layer unique property object to an atom level object;
 */
public class UniquePropertyTranslator {
	public void translate(@Nullable UniqueProperty uniqueProperty, AtomBuilder atomBuilder) {
		Objects.requireNonNull(atomBuilder);

		if (uniqueProperty == null) {
			return;
		}

		Payload payload = new Payload(uniqueProperty.getUnique());
		ECPublicKey ecPublicKey = uniqueProperty.getAddress().getPublicKey();
		UniqueParticle uniqueParticle = UniqueParticle.create(payload, ecPublicKey);
		atomBuilder.addParticle(uniqueParticle);
		return;
	}
}
