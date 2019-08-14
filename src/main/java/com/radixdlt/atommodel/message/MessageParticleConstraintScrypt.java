package com.radixdlt.atommodel.message;

import com.radixdlt.atommodel.procedures.NonRRIResourceCreation;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Result;

public class MessageParticleConstraintScrypt implements ConstraintScrypt {
	@Override
	public void main(SysCalls os) {
		os.registerParticleMultipleAddresses(
			MessageParticle.class,
			MessageParticle::getAddresses,
			m -> {
				if (m.getBytes() == null) {
					return Result.error("message data is null");
				}

				return Result.success();
			}
		);

		os.createTransition(
			null,
			MessageParticle.class,
			new NonRRIResourceCreation<>(),
			(res, in, out, meta) -> meta.isSignedBy(out.getFrom())
		);
	}
}
