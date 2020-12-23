/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.application.translate.StageActionException;
import com.radixdlt.identifiers.RadixAddress;

/**
 * An exception raised when a given stake action cannot be executed
 */
public class StakeNotPossibleException extends StageActionException {
	private final RadixAddress delegate;

	private StakeNotPossibleException(String message, RadixAddress delegate) {
		super(message);
		this.delegate = delegate;
	}

	public static StakeNotPossibleException notRegistered(RadixAddress delegate) {
		return new StakeNotPossibleException("delegate is not registered as a validator: " + delegate, delegate);
	}

	public static StakeNotPossibleException notAllowed(RadixAddress delegate, RadixAddress delegator) {
		return new StakeNotPossibleException(String.format(
			"delegate %s does not allow this delegator %s", delegate, delegator), delegate);
	}

	public RadixAddress getDelegate() {
		return delegate;
	}
}
