package com.radixdlt.client.application.translate.validators;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.identifiers.RadixAddress;

/**
 * Unregisters the given address as a validator. Validator must be registered already.
 * To register a validator, use {@link RegisterValidatorAction}.
 */
public class UnregisterValidatorAction implements Action {
	private final RadixAddress validator;

	public UnregisterValidatorAction(RadixAddress validator) {
		this.validator = validator;
	}

	public RadixAddress getValidator() {
		return validator;
	}

	@Override
	public String toString() {
		return String.format("UNREGISTER VALIDATOR %s", validator);
	}
}
