/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.engine;

import com.radixdlt.atom.Txn;
import com.radixdlt.constraintmachine.CMError;

import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Exception thrown by Radix Engine
 */
@SuppressWarnings("serial")
public final class RadixEngineException extends Exception {
	private final RadixEngineErrorCode errorCode;
	private final CMError cmError;

	public RadixEngineException(
		Txn txn, RadixEngineErrorCode errorCode, String message
	) {
		this(txn, errorCode, message, null);
	}

	public RadixEngineException(
		Txn txn,
		RadixEngineErrorCode errorCode,
		String message,
		CMError cmError
	) {
		super(message + " " + (cmError == null ? "" : "\n" + cmError) + "\n" + txn);
		this.errorCode = Objects.requireNonNull(errorCode);
		this.cmError = cmError;
	}

	/**
	 * Retrieve the high-level error code associated with the exception
	 * @return the error code
	 */
	public RadixEngineErrorCode getErrorCode() {
		return this.errorCode;
	}

	/**
	 * Get the {@link CMError} related to this exception
	 *
	 * @return the constraint machine error
	 */
	@Nullable
	public CMError getCmError() {
		return cmError;
	}
}
