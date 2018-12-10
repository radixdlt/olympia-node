package com.radixdlt.client.application.translate;

import com.google.gson.JsonObject;
import com.radixdlt.client.core.atoms.Atom;
import java.util.stream.Stream;

public interface AtomErrorToExceptionReasonMapper {
	Stream<ActionExecutionExceptionReason> mapAtomErrorToExceptionReasons(Atom atom, JsonObject errorData);
}
