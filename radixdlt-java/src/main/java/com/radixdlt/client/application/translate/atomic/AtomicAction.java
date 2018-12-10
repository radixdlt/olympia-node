package com.radixdlt.client.application.translate.atomic;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.core.atoms.RadixHash;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.radix.utils.RadixConstants;

public class AtomicAction implements Action {
	private final List<Action> actions;

	public AtomicAction(Action... actions) {
		this.actions = Collections.unmodifiableList(Arrays.asList(actions));
	}

	public List<Action> getActions() {
		return actions;
	}
}
