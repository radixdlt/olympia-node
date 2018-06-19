package com.radixdlt.client.core.atoms;

public interface AtomValidator {
	void validate(Atom atom) throws AtomValidationException;
}
