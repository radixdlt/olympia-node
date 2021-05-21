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

package com.radixdlt.constraintmachine;

import com.radixdlt.constraintmachine.ConstraintMachine.CMValidationState;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * An error with a pointer to the issue
 */
public final class CMError {
	private final int instructionIndex;
	private final CMErrorCode errorCode;
	private final String errMsg;
	private final CMValidationState cmValidationState;

	public CMError(
		int instructionIndex,
		CMErrorCode errorCode,
		CMValidationState cmValidationState
	) {
		this(instructionIndex, errorCode, cmValidationState, null);
	}

	public CMError(
		int instructionIndex,
		CMErrorCode errorCode,
		CMValidationState cmValidationState,
		String errMsg
	) {
		this.errorCode = Objects.requireNonNull(errorCode);
		this.instructionIndex = instructionIndex;
		this.errMsg = errMsg;
		this.cmValidationState = cmValidationState;
	}

	@Nullable
	public String getErrMsg() {
		return errMsg;
	}

	public CMErrorCode getErrorCode() {
		return errorCode;
	}

	public String getErrorDescription() {
		return errorCode.getDescription() + (errMsg == null ? "" : ": " + errMsg);
	}

	public CMValidationState getCmValidationState() {
		return cmValidationState;
	}

	@Override
	public int hashCode() {
		return Objects.hash(errMsg, instructionIndex, errorCode, cmValidationState);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof CMError)) {
			return false;
		}

		CMError e = (CMError) o;
		return Objects.equals(e.errMsg, this.errMsg)
			&& e.instructionIndex == this.instructionIndex
			&& Objects.equals(e.errorCode, this.errorCode)
			&& Objects.equals(e.cmValidationState, this.cmValidationState);
	}

	@Override
	public String toString() {
		return "Error=" + errorCode + " Msg=" + getErrMsg() + " State=" + cmValidationState + " Index=" + instructionIndex;
	}
}
