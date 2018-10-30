package com.radixdlt.client.application.translate;

import com.radixdlt.client.application.actions.UniqueProperty;
import com.radixdlt.client.core.atoms.particles.Particle;
import io.reactivex.annotations.Nullable;

import java.util.List;

/**
 * Translates an application layer unique property object to an atom level object;
 */
public class UniquePropertyTranslator {
	public List<Particle> map(@Nullable UniqueProperty uniqueProperty) {
		if (uniqueProperty == null) {
			return null;
		}

		throw new UnsupportedOperationException("UniqueParticles are currently not supported"); // TODO
		/*if (uniqueProperty == null) {
			return Collections.emptyList();
		}

		byte[] payload = uniqueProperty.getUnique();
		ECPublicKey ecPublicKey = uniqueProperty.getAddress().getPublicKey();
		UniqueParticle uniqueParticle = UniqueParticle.create(payload, ecPublicKey);
		return Collections.singletonList(uniqueParticle);*/
	}
}
