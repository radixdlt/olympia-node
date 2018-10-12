package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomFeeConsumableBuilder;
import com.radixdlt.client.core.atoms.particles.AtomFeeConsumable;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.crypto.ECPublicKey;
import java.util.Collections;
import java.util.List;

public class PowFeeMapper implements FeeMapper {
	public List<Particle> map(List<Particle> particles, RadixUniverse universe, ECPublicKey key) {
		Atom atom = new Atom(particles);
		AtomFeeConsumable fee = new AtomFeeConsumableBuilder()
			.powToken(universe.getPOWToken())
			.atom(atom)
			.owner(key)
			.pow(universe.getMagic(), 16)
			.build();
		return Collections.singletonList(fee);
	}
}
