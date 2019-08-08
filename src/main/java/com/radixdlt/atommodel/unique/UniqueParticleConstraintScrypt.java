package com.radixdlt.atommodel.unique;

import com.radixdlt.atomos.AtomOS;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Result;

public class UniqueParticleConstraintScrypt implements ConstraintScrypt {
	@Override
	public void main(AtomOS os) {
		os.registerParticle(UniqueParticle.class, UniqueParticle::getAddress);

		os.onIndexed(UniqueParticle.class, UniqueParticle::getRRI)
			.requireInitial((unique, meta) ->
				Result.of(meta.isSignedBy(unique.getAddress()), "Owner has to sign: " + unique.getAddress())
			);
	}
}
