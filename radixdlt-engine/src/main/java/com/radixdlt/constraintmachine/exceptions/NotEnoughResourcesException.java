/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.constraintmachine.exceptions;

import com.radixdlt.utils.UInt256;

public final class NotEnoughResourcesException extends Exception {
	private final UInt256 request;
	private final UInt256 amount;

	public NotEnoughResourcesException(UInt256 request, UInt256 amount) {
		super("request: " + request + " amount: " + amount);
		this.request = request;
		this.amount = amount;
	}

	public UInt256 getRequest() {
		return request;
	}

	public UInt256 getAmount() {
		return amount;
	}
}
