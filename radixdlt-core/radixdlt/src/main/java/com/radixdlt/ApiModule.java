/*
 * (C) Copyright 2020 Radix DLT Ltd
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
 */

package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.statecomputer.AtomCommittedToLedger;
import org.radix.api.http.RadixHttpServer;

/**
 * Configures the api including http server setup
 */
public final class ApiModule extends AbstractModule {
	@Override
	public void configure() {
		bind(RadixHttpServer.class).in(Scopes.SINGLETON);
		var eventBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<Class<?>>() { }, LocalEvents.class)
				.permitDuplicates();
		eventBinder.addBinding().toInstance(AtomCommittedToLedger.class);
		eventBinder.addBinding().toInstance(MempoolAddFailure.class);
	}
}
