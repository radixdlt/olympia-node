package com.radixdlt.client.application.translate.validators;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.identifiers.RadixAddress;

public class RegisterValidatorAction implements Action {
	private final RadixAddress validator;

	public RegisterValidatorAction(RadixAddress validator) {
		this.validator = validator;
	}

	public RadixAddress getValidator() {
		return validator;
	}

	@Override
	public String toString() {
		return String.format("REGISTER VALIDATOR %s", validator);
	}
}
