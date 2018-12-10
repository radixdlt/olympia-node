package com.radixdlt.client.application.translate.atomic;

import com.radixdlt.client.application.translate.Action;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AtomicAction implements Action {
	private final List<Action> actions;

	public AtomicAction(Action... actions) {
		Objects.requireNonNull(actions);
		this.actions = Collections.unmodifiableList(Arrays.asList(actions));
	}

	public List<Action> getActions() {
		return actions;
	}
}
