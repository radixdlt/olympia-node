package com.radixdlt.atommodel.message;

import com.radixdlt.atommodel.procedures.NonRRIResourceCreation;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Result;

public class MessageParticleConstraintScrypt implements ConstraintScrypt {
	@Override
	public void main(SysCalls os) {
		os.registerParticleMultipleAddress(
			MessageParticle.class,
			MessageParticle::getAddresses,
			m -> {
				if (m.getBytes() == null) {
					return Result.error("message data is null");
				}

				return Result.success();
			}
		);

		os.newTransition(
			null,
			MessageParticle.class,
			new NonRRIResourceCreation<>(
				(msg, meta) -> {
					if (!meta.isSignedBy(msg.getFrom())) {
						return Result.error("message must be signed by sender: " + msg.getFrom());
					}

					return Result.success();
				}
			)
		);
	}
}
