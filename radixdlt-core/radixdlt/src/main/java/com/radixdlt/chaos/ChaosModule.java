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
 */

package com.radixdlt.chaos;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerModule;
import com.radixdlt.chaos.messageflooder.MessageFlooderModule;
import com.radixdlt.api.Controller;

/**
 * Module for chaos type functions
 */
public final class ChaosModule extends AbstractModule {
	@Override
	public void configure() {
		var controllers = Multibinder.newSetBinder(binder(), Controller.class);
		controllers.addBinding().to(ChaosController.class).in(Scopes.SINGLETON);
		install(new MessageFlooderModule());
		install(new MempoolFillerModule());
	}
}
