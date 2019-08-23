package com.radixdlt.atomos;

import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.universe.Universe;
import java.util.Objects;
import com.radixdlt.crypto.Hash;
import com.radixdlt.utils.POW;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * An implementation of an AtomOS Kernel level "driver" which adds constraints
 * to low level atom properties such as size, fees, signatures, etc.
 */
public final class AtomDriver implements AtomOSDriver {
	private final boolean skipAtomFeeCheck;
	private final Supplier<Universe> universeSupplier;
	private final LongSupplier timestampSupplier;
	private static final Hash DEFAULT_TARGET = new Hash("0000FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
	private final int maximumDrift;

	public AtomDriver(
		Supplier<Universe> universeSupplier,
		LongSupplier timestampSupplier,
		boolean skipAtomFeeCheck,
		int maximumDrift
	) {
		this.universeSupplier = universeSupplier;
		this.timestampSupplier = timestampSupplier;
		this.skipAtomFeeCheck = skipAtomFeeCheck;
		this.maximumDrift = maximumDrift;
	}

	@Override
	public void main(AtomOSKernel kernel) {
		kernel.onAtom()
			.setCompute(atom -> {
				//TODO: Fix module loadup sequence so that massFunction doesn't need to be recreated everytime
				final FungibleOrHashMassFunction massFunction = new FungibleOrHashMassFunction(universeSupplier.get());
				return massFunction.getMass(atom.getCMInstruction());
			});

		// Atom has particles
		kernel.onAtom()
			.require(cmAtom -> {
				if (cmAtom.getCMInstruction().getParticles().isEmpty()) {
					return Result.error("atom has no particles");
				}

				return Result.success();
			});

		kernel.onAtom()
			.require(cmAtom -> {
				final boolean isMagic = Objects.equals(cmAtom.getCMInstruction().getMetaData().get("magic"), "0xdeadbeef");

				// Atom has signatures
				if (cmAtom.getCMInstruction().getSignatures().isEmpty() && !isMagic) {
					return Result.error("atom has no signatures");
				}

				// Atom has fee
				if (!skipAtomFeeCheck) {
					if (universeSupplier.get().getGenesis().stream()
						.map(ImmutableAtom::getAID).anyMatch(cmAtom.getCMInstruction().getAID()::equals) || isMagic) {
						// genesis and magic atoms don't need a fee
						return Result.success();
					}

					String powNonceString = cmAtom.getCMInstruction().getMetaData().get(CMInstruction.METADATA_POW_NONCE_KEY);
					if (powNonceString == null) {
						return Result.error("atom fee missing, metadata does not contain '" + ImmutableAtom.METADATA_POW_NONCE_KEY + "'");
					}

					try {
						final long powNonce = Long.parseLong(powNonceString);
						final Hash powFeeHash = cmAtom.getCMInstruction().getPowFeeHash();
						POW pow = new POW(universeSupplier.get().getMagic(), powFeeHash, powNonce);

						return checkPow(pow, powFeeHash, DEFAULT_TARGET, universeSupplier.get().getMagic());
					} catch (NumberFormatException e) {
						return Result.error("atom fee invalid, metadata contains invalid powNonce: " + powNonceString);
					}
				}

				return Result.success();
			});

		// Atom timestamp constraints
		kernel.onAtom()
			.require(cmAtom -> {
				String timestampString = cmAtom.getCMInstruction().getMetaData().get(ImmutableAtom.METADATA_TIMESTAMP_KEY);
				if (timestampString == null) {
					return Result.error("atom metadata does not contain '" + ImmutableAtom.METADATA_TIMESTAMP_KEY + "'");
				}
				try {
					long timestamp = Long.parseLong(timestampString);

					if (timestamp > timestampSupplier.getAsLong()
						+ TimeUnit.MILLISECONDS.convert(maximumDrift, TimeUnit.SECONDS)) {
						return Result.error("atom metadata timestamp is after allowed drift time");
					}
					if (timestamp < universeSupplier.get().getTimestamp()) {
						return Result.error("atom metadata timestamp is before universe creation");
					}
				} catch (NumberFormatException e) {
					return Result.error("atom metadata contains invalid timestamp: " + timestampString);
				}

				return Result.success();
			});
	}

	private static Result checkPow(POW pow, Hash hash, Hash target, int universeMagic) {
		if (!pow.getSeed().equals(hash)) {
			return Result.error("atom fee invalid: seed does not match validation parameter seed (" + pow.getSeed() + ") + hash(" + hash + ")");
		}

		if (pow.getMagic() != universeMagic) {
			return Result.error("atom fee invalid: magic '" + pow.getMagic() + "' does not match Universe magic '");
		}

		if (pow.getHash().compareTo(target) >= 0) {
			return Result.error("atom fee invalid: '" + pow.getHash() + "' does not meet target '" + target + "'");
		}

		return Result.success();
	}
}
