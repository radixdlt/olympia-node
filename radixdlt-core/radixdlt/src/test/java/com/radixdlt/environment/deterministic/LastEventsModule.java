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

package com.radixdlt.environment.deterministic;

import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.MutableClassToInstanceMap;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.environment.EventProcessorOnDispatch;

public final class LastEventsModule extends AbstractModule {
	private final Class<?>[] messageClasses;

	public LastEventsModule(Class<?>... messageClasses) {
		this.messageClasses = messageClasses;
	}

	@Override
	protected void configure() {
		var map = MutableClassToInstanceMap.create();
		bind(new TypeLiteral<ClassToInstanceMap<Object>>() { })
			.toInstance(map);

		for (var c : messageClasses) {
			Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessorOnDispatch<?>>() { })
				.addBinding()
				.toInstance(new EventProcessorOnDispatch<>(c, e -> map.put(c, e)));
		}
	}
}
