package com.radixdlt.atommodel.message;

import com.radixdlt.atomos.AtomOS;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.ConstraintScrypt;

public class MessageParticleConstraintScrypt implements ConstraintScrypt {
	@Override
	public void main(AtomOS os) {
		os.registerParticle(MessageParticle.class, "message", MessageParticle::getAddresses);

		os.onPayload(MessageParticle.class)
			.require((msg, meta) -> {
				if (!meta.isSignedBy(msg.getFrom())) {
					return Result.error("message must be signed by sender: " + msg.getFrom());
				}
				if (msg.getBytes() == null) {
					return Result.error("message data is null");
				}

				return Result.success();
			});
	}
}
