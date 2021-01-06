/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.application.translate.validators;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.identifiers.RadixAddress;

import java.util.Objects;
import java.util.Set;

/**
 * Registers the given address as a validator. Validator must not be registered already.
 * To unregister a validator, use {@link UnregisterValidatorAction}.
 */
public class RegisterValidatorAction implements Action {
	private final RadixAddress validator;
	private final Set<RadixAddress> allowedDelegators;
	private final String url;

	public RegisterValidatorAction(RadixAddress validator, Set<RadixAddress> allowedDelegators, String url) {
		this.validator = Objects.requireNonNull(validator);
		this.allowedDelegators = Objects.requireNonNull(allowedDelegators);
		this.url = url;
	}

	public RadixAddress getValidator() {
		return validator;
	}

	public Set<RadixAddress> getAllowedDelegators() {
		return allowedDelegators;
	}

	public String getUrl() {
		return url;
	}

	@Override
	public String toString() {
		return String.format("REGISTER VALIDATOR %s", validator);
	}
}
