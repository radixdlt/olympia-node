package com.radixdlt.atommodel.message;

import com.radixdlt.atomos.SysCalls;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Result;

public class MessageParticleConstraintScrypt implements ConstraintScrypt {
	@Override
	public void main(SysCalls os) {
		os.registerParticle(MessageParticle.class, MessageParticle::getAddresses);
		os.newResourceType(
			MessageParticle.class,
			(msg, meta) -> {
				if (!meta.isSignedBy(msg.getFrom())) {
					return Result.error("message must be signed by sender: " + msg.getFrom());
				}

				return Result.success();
			}
		);

		os.on(MessageParticle.class)
			.require(p -> {
				if (p.getBytes() == null) {
					return Result.error("message data is null");
				}

				return Result.success();
			});
	}
}
