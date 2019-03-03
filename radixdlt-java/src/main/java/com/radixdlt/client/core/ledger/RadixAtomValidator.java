package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomValidationException;
import com.radixdlt.client.core.atoms.AtomValidator;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.ECSignature;
import java.util.Objects;
import java.util.Optional;

public class RadixAtomValidator implements AtomValidator {
	private static final RadixAtomValidator VALIDATOR = new RadixAtomValidator();

	public static RadixAtomValidator getInstance() {
		return VALIDATOR;
	}

	private RadixAtomValidator() {
	}

	/**
	 * Checks the owners of each AbstractConsumable particle and makes sure the
	 * atom contains signatures for each owner
	 *
	 * @param atom atom to validate
	 * @throws AtomValidationException if atom has missing/bad signatures for a particle
	 */
	public void validateSignatures(Atom atom) throws AtomValidationException {
		RadixHash hash = atom.hash();

		Optional<AtomValidationException> exception = atom.getConsumableParticles(Spin.DOWN).stream()
			.map(down -> {
				ECPublicKey owner = down.getOwner();
				if (owner == null) {
					return new AtomValidationException("No owner");
				}

				if (down.getTokenTypeReference().getSymbol().equals("POW")) {
					return null;
				}

				Optional<ECSignature> signature = atom.getSignature(owner.getUID());
				if (!signature.isPresent()) {
					return new AtomValidationException("Missing signature");
				}

				if (!hash.verifySelf(owner, signature.get())) {
					return new AtomValidationException("Bad signature");
				}

				return null;
			})
		.filter(Objects::nonNull)
		.findAny();

		if (exception.isPresent()) {
			throw exception.get();
		}
	}

	@Override
	public void validate(Atom atom) throws AtomValidationException {
		// TODO: check with universe genesis timestamp
		if (!atom.hasTimestamp()) {
			throw new AtomValidationException("Atom does not contain a timestamp");
		}

		if (atom.getTimestamp() < 0L) {
			throw new AtomValidationException("Atom timestamp must be positive " + atom.getTimestamp());
		}

		validateSignatures(atom);
	}
}
