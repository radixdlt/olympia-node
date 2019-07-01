package com.radixdlt.client.application.translate.unique;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.core.atoms.particles.RRI;
import java.util.Objects;

public class PutUniqueIdAction implements Action {
	private final RRI rri;

	private PutUniqueIdAction(RRI rri) {
		Objects.requireNonNull(rri);

		this.rri = rri;
	}

	public static PutUniqueIdAction create(RRI rri) {
		return new PutUniqueIdAction(rri);
	}

	public RRI getRRI() {
		return rri;
	}

	@Override
	public String toString() {
		return "PUT UNIQUE " + rri;
	}
}
