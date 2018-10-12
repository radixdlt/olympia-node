package com.radixdlt.client.application.translate;

import com.radixdlt.client.application.actions.UniqueProperty;
import com.radixdlt.client.core.atoms.Payload;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.UniqueParticle;
import com.radixdlt.client.core.crypto.ECPublicKey;
import io.reactivex.annotations.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Translates an application layer unique property object to an atom level object;
 */
public class UniquePropertyTranslator {
	public List<Particle> map(@Nullable UniqueProperty uniqueProperty) {
		if (uniqueProperty == null) {
			return Collections.emptyList();
		}

		Payload payload = new Payload(uniqueProperty.getUnique());
		ECPublicKey ecPublicKey = uniqueProperty.getAddress().getPublicKey();
		UniqueParticle uniqueParticle = UniqueParticle.create(payload, ecPublicKey);
		return Collections.singletonList(uniqueParticle);
	}
}
