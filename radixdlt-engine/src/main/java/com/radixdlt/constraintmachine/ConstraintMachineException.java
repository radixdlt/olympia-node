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

package com.radixdlt.constraintmachine;

public class ConstraintMachineException extends Exception {
	private final CMErrorCode errorCode;

	public ConstraintMachineException(CMErrorCode errorCode, Throwable cause) {
		super(errorCode.toString(), cause);
		this.errorCode = errorCode;
	}

	public ConstraintMachineException(CMErrorCode errorCode) {
		super(errorCode.toString());
		this.errorCode = errorCode;
	}

	public ConstraintMachineException(CMErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public CMErrorCode getErrorCode() {
		return errorCode;
	}
}
