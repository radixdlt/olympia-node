package org.radix.validation;

import java.util.Objects;
import com.radixdlt.atoms.Atom;
import com.radixdlt.atoms.DataPointer;
import org.radix.exceptions.ValidationException;

/**
 * An exception during validation of an {@link Atom}
 */
public class ConstraintMachineValidationException extends ValidationException {
	private final Atom atom;
	private final DataPointer dataPointer;

	public ConstraintMachineValidationException(Atom atom, String message, DataPointer dataPointer) {
		super(message);

		Objects.requireNonNull(atom);
		Objects.requireNonNull(dataPointer);

		dataPointer.validateExists(atom);

		this.atom = atom;
		this.dataPointer = dataPointer;
	}

	public String getPointerToIssue() {
		return dataPointer.toString();
	}

	public Atom getAtom() {
		return atom;
	}
}