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

import java.util.Optional;

public class DefaultedSystemLoanException extends Exception {
	public DefaultedSystemLoanException(DepletedFeeReserveException cause, UInt256 feeDeposited) {
		super("Reserve fee deposit " + feeDeposited + " not enough to cover basic txn fee of "
			+ Optional.ofNullable(feeDeposited).orElse(UInt256.ZERO).add(cause.getMissingAmount()));
	}
}
