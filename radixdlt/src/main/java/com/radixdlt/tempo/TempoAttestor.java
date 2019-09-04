package com.radixdlt.tempo;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.utils.Pair;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import org.radix.exceptions.ValidationException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.time.TemporalProof;
import org.radix.time.TemporalVertex;
import org.radix.universe.system.LocalSystem;

import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

public final class TempoAttestor implements Attestor {
	private static final Logger logger = Logging.getLogger("tempo.attestor");

	private final LocalSystem localSystem;
	private final WallclockTimeSupplier wallclockTimeSupplier;

	@Inject
	public TempoAttestor(
		@Named("self") LocalSystem localSystem,
		WallclockTimeSupplier wallclockTimeSupplier
	) {
		this.localSystem = Objects.requireNonNull(localSystem, "localSystem is required");
		this.wallclockTimeSupplier = Objects.requireNonNull(wallclockTimeSupplier, "wallclockTimeSupplier");
	}

	public TemporalCommitment attestTo(AID aid) {
		long wallclockTime = wallclockTimeSupplier.getAsLong();
		Pair<Long, Hash> clockAndCommitment = this.localSystem.update(aid, wallclockTime);
		return new TemporalCommitment(aid, clockAndCommitment.getFirst(), clockAndCommitment.getSecond());
	}
}
