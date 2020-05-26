package com.radixdlt.client.application.translate.validators;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.identifiers.RadixAddress;

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
