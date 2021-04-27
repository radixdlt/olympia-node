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
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.radixdlt.utils.TimeSupplier;
import com.radixdlt.properties.RuntimeProperties;
import java.security.SecureRandom;
import java.util.Random;
import org.radix.time.Time;

/**
 * Module which specifies implementations of system objects such as
 * random and time.
 */
public class SystemModule extends AbstractModule {
	@Override
	public void configure() {
		bind(Random.class).to(SecureRandom.class).in(Scopes.SINGLETON);
	}

	@Provides
	@Singleton
	TimeSupplier time(RuntimeProperties properties) {
		Time.start(properties);
		return Time::currentTimestamp;
	}
}
