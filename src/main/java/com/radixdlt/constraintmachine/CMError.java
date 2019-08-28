package com.radixdlt.constraintmachine;

import com.radixdlt.constraintmachine.ConstraintMachine.CMValidationState;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * An error with a pointer to the issue
 */
public final class CMError {
	private final DataPointer dataPointer;
	private final CMErrorCode errorCode;
	private final String errMsg;
	private final CMValidationState cmValidationState;

	public CMError(
		DataPointer dataPointer,
		CMErrorCode errorCode,
		CMValidationState cmValidationState
	) {
		this(dataPointer, errorCode, cmValidationState, null);
	}

	public CMError(
		DataPointer dataPointer,
		CMErrorCode errorCode,
		CMValidationState cmValidationState,
		String errMsg
	) {
		this.errorCode = Objects.requireNonNull(errorCode);
		this.dataPointer = Objects.requireNonNull(dataPointer);
		this.errMsg = errMsg;
		this.cmValidationState = cmValidationState;
	}

	@Nullable
	public String getErrMsg() {
		return errMsg;
	}

	public DataPointer getDataPointer() {
		return dataPointer;
	}

	public CMErrorCode getErrorCode() {
		return errorCode;
	}

	public String getErrorDescription() {
		return errorCode.getDescription() + (errMsg == null ? "" : ": " + errMsg);
	}

	@Override
	public int hashCode() {
		return Objects.hash(errMsg, dataPointer, errorCode, cmValidationState);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof CMError)) {
			return false;
		}

		CMError e = (CMError) o;
		return Objects.equals(e.errMsg, this.errMsg)
			&& Objects.equals(e.dataPointer, this.dataPointer)
			&& Objects.equals(e.errorCode, this.errorCode)
			&& Objects.equals(e.cmValidationState, this.cmValidationState);
	}

	@Override
	public String toString() {
		return dataPointer + ": " + errorCode + " " + getErrMsg();
	}
}
