package com.radixdlt.tempo;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.common.AID;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECSignature;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;
import com.radixdlt.utils.Pair;
import com.radixdlt.crypto.Hash;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.universe.system.LocalSystem;

import java.util.Objects;

public final class TempoAttestor implements Attestor {
	private static final Logger logger = Logging.getLogger("tempo.attestor");

	private final LocalSystem localSystem;
	private final Serialization serialization;
	private final WallclockTimeSupplier wallclockTimeSupplier;

	@Inject
	public TempoAttestor(
		@Named("self") LocalSystem localSystem,
		Serialization serialization,
		WallclockTimeSupplier wallclockTimeSupplier
	) {
		this.localSystem = Objects.requireNonNull(localSystem);
		this.serialization = Objects.requireNonNull(serialization);
		this.wallclockTimeSupplier = Objects.requireNonNull(wallclockTimeSupplier);
	}

	public TemporalCommitment attestTo(AID aid) {
		Pair<Long, Hash> clockAndCommitment = this.localSystem.update(aid, wallclockTimeSupplier.getAsLong());
		UnsignedTemporalCommitment unsignedCommitment = new UnsignedTemporalCommitment(aid, clockAndCommitment.getFirst(), clockAndCommitment.getSecond());
		try {
			byte[] commitmentData = serialization.toDson(unsignedCommitment, DsonOutput.Output.HASH);
			ECSignature signature = localSystem.getKeyPair().sign(commitmentData);
			return unsignedCommitment.sign(signature);
		} catch (SerializationException | CryptoException e) {
			throw new TempoException("Error while signing temporal commitment for '" + aid + "'", e);
		}
	}
}
