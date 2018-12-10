package com.radixdlt.client.application.translate.unique;

import com.radixdlt.client.application.translate.ActionExecutionExceptionReason;
import com.radixdlt.client.atommodel.unique.UniqueId;
import java.util.Objects;

public class AlreadyUsedUniqueIdReason extends ActionExecutionExceptionReason {
	private final UniqueId uniqueId;

	public AlreadyUsedUniqueIdReason(UniqueId uniqueId) {
		super(Objects.requireNonNull(uniqueId) + " already used.");

		this.uniqueId = uniqueId;
	}

	@Override
	public int hashCode() {
		return uniqueId.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof AlreadyUsedUniqueIdReason)) {
			return false;
		}

		AlreadyUsedUniqueIdReason alreadyUsedUniqueIdReason = (AlreadyUsedUniqueIdReason) o;
		return alreadyUsedUniqueIdReason.uniqueId.equals(this.uniqueId);
	}
}
