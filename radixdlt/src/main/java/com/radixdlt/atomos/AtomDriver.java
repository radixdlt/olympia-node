package com.radixdlt.atomos;

import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;
import java.util.Objects;
import com.radixdlt.atoms.Atom;
import com.radixdlt.crypto.Hash;
import com.radixdlt.utils.POW;

import java.util.concurrent.TimeUnit;

/**
 * An implementation of an AtomOS Kernel level "driver" which adds constraints
 * to low level atom properties such as size, fees, signatures, etc.
 */
public final class AtomDriver implements AtomOSDriver {
	static final int MAX_ATOM_SIZE = 1024 * 1024;
	private final boolean skipAtomFeeCheck;
	private final Serialization serialization;
	private final static Hash DEFAULT_TARGET = new Hash("0000FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
	private final int maximumDrift;

	public AtomDriver(Serialization serialization, boolean skipAtomFeeCheck, int maximumDrift) {
		this.serialization = serialization;
		this.skipAtomFeeCheck = skipAtomFeeCheck;
		this.maximumDrift = maximumDrift;
	}

	@Override
	public void main(AtomOSKernel kernel) {
		kernel.onAtom()
			.compute("mass", atom -> {
				//TODO: Fix module loadup sequence so that massFunction doesn't need to be recreated everytime
				final FungibleOrHashMassFunction massFunction = new FungibleOrHashMassFunction(kernel.getUniverse());
				return massFunction.getMass(atom);
			});

		// Atom size is below limit
		kernel.onAtom()
			.require(cmAtom -> {
				// TODO: remove reserialization of atom
				final int computedSize;
				try {
					computedSize = serialization.toDson(cmAtom.getAtom(), Output.PERSIST).length;
				} catch (SerializationException e) {
					throw new IllegalStateException("Could not compute size", e);
				}
				if (computedSize > MAX_ATOM_SIZE) {
					return Result.error("atom size is too large: " + computedSize + " > " + MAX_ATOM_SIZE);
				}

				return Result.success();
			});

		// Atom has particles
		kernel.onAtom()
			.require(cmAtom -> {
				if (cmAtom.getAtom().spunParticles().count() <= 0) {
					return Result.error("atom has no particles");
				}

				return Result.success();
			});

		kernel.onAtom()
			.require(cmAtom -> {
				final Atom atom = cmAtom.getAtom();
				final boolean isMagic = Objects.equals(atom.getMetaData().get("magic"), "0xdeadbeef");

				// Atom has signatures
				if (atom.getSignatures().isEmpty() && !isMagic) {
					return Result.error("atom has no signatures");
				}

				// Atom has fee
				if (!skipAtomFeeCheck) {
					if (kernel.getUniverse().getGenesis().contains(atom) || isMagic) {
						// genesis and magic atoms don't need a fee
						return Result.success();
					}

					String powNonceString = atom.getMetaData().get(Atom.METADATA_POW_NONCE_KEY);
					if (powNonceString == null) {
						return Result.error("atom fee missing, metadata does not contain '" + Atom.METADATA_POW_NONCE_KEY + "'");
					}

					try {
						long powNonce = Long.parseLong(powNonceString);
						Hash powFeeHash = atom.copyExcludingMetadata(Atom.METADATA_POW_NONCE_KEY).getHash();
						POW pow = new POW(kernel.getUniverse().getMagic(), powFeeHash, powNonce);

						return checkPow(pow, powFeeHash, DEFAULT_TARGET, kernel.getUniverse().getMagic());
					} catch (NumberFormatException e) {
						return Result.error("atom fee invalid, metadata contains invalid powNonce: " + powNonceString);
					}
				}

				return Result.success();
			});

		// Atom timestamp constraints
		kernel.onAtom()
			.require(cmAtom -> {
				final Atom atom = cmAtom.getAtom();
				String timestampString = atom.getMetaData().get(Atom.METADATA_TIMESTAMP_KEY);
				if (timestampString == null) {
					return Result.error("atom metadata does not contain '" + Atom.METADATA_TIMESTAMP_KEY + "'");
				}
				try {
					long timestamp = Long.parseLong(timestampString);

					if (timestamp > kernel.getCurrentTimestamp()
						+ TimeUnit.MILLISECONDS.convert(maximumDrift, TimeUnit.SECONDS)) {
						return Result.error("atom metadata timestamp is after allowed drift time");
					}
					if (timestamp < kernel.getUniverse().getTimestamp()) {
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
