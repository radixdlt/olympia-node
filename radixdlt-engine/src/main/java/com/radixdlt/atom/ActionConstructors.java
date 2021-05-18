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

package com.radixdlt.atom;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Set of action to constructors mapper
 */
public final class ActionConstructors {
	private final Map<Class<? extends TxAction>, ActionConstructor<?>> constructors;

	private ActionConstructors(Map<Class<? extends TxAction>, ActionConstructor<?>> constructors) {
		this.constructors = constructors;
	}

	public static Builder newBuilder() {
		return new Builder();
	}

	public static class Builder {
		private ImmutableMap.Builder<Class<? extends TxAction>, ActionConstructor<?>> mapBuilder = ImmutableMap.builder();
		private Builder() {
		}

		public <T extends TxAction> Builder put(Class<T> actionClass, ActionConstructor<T> constructor) {
			mapBuilder.put(actionClass, constructor);
			return this;
		}

		public ActionConstructors build() {
			return new ActionConstructors(mapBuilder.build());
		}
	}

	public <T extends TxAction> void construct(T action, TxBuilder txBuilder) throws TxBuilderException {
		var actionConstructor = (ActionConstructor<T>) constructors.get(action.getClass());
		if (actionConstructor == null) {
			throw new IllegalArgumentException("Constructor not found for " + action);
		}
		actionConstructor.construct(action, txBuilder);
	}
}
