package com.radixdlt.serialization;

import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atomos.RRIParticle;
import org.radix.atoms.Atom;
import com.radixdlt.atomos.RRI;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.universe.Universe;
import org.radix.atoms.particles.conflict.ParticleConflict;
import org.radix.atoms.particles.conflict.messages.ConflictAssistResponseMessage;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.CryptoException;
import org.radix.modules.Modules;

/**
 * Check serialization of ConflictAssistMessage
 */
public class ConflictMessageSerializeTest extends SerializeObject<ConflictAssistResponseMessage> {
	public ConflictMessageSerializeTest() {
		super(ConflictAssistResponseMessage.class, ConflictMessageSerializeTest::get);
	}

	private static ConflictAssistResponseMessage get() {
		try {
			ECKeyPair key = new ECKeyPair();
			RadixAddress ar = RadixAddress.from(Modules.get(Universe.class), key.getPublicKey());
			SpunParticle<RRIParticle> particle = SpunParticle.up(new RRIParticle(RRI.of(ar, "hi")));
			Atom conflictor = new Atom();
			conflictor.sign(key);
			Atom invoker = new Atom();
			invoker.sign(key);
			ParticleConflict pc = new ParticleConflict(particle, conflictor, invoker);
			return new ConflictAssistResponseMessage(pc);
		} catch (CryptoException e) {
			throw new IllegalStateException("Can't create ParticleConflict", e);
		}
	}
}
