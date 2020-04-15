/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.network.addressbook;

import org.radix.database.DatabaseEnvironment;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.serialization.Serialization;

import java.util.Objects;

/**
 * Guice configuration for {@link AddressBook}.
 */
public final class AddressBookModule extends AbstractModule {

	private final DatabaseEnvironment dbEnv;

	public AddressBookModule(DatabaseEnvironment dbEnv) {
		// Nothing
		this.dbEnv = Objects.requireNonNull(dbEnv);
	}

	@Override
	protected void configure() {
		// The main target
		bind(AddressBook.class).to(AddressBookImpl.class).in(Singleton.class);

		// Bind DatabaseEnvironment to global instance
		// This is temporary as we're in the progress of untangling the dependency and DI mess
		bind(DatabaseEnvironment.class).toInstance(dbEnv);
	}

	@Provides
	@Singleton
	PeerPersistence addressBookPersistenceProvider(Serialization serialization, DatabaseEnvironment dbEnv) {
		AddressBookPersistence persistence = new AddressBookPersistence(serialization, dbEnv);
		persistence.start();
		return persistence;
	}
}
