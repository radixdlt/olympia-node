package org.radix.integration;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import com.radixdlt.common.EUID;
import com.radixdlt.consensus.tempo.Application;
import com.radixdlt.consensus.tempo.Tempo;
import com.radixdlt.delivery.LazyRequestDeliverer;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.store.berkeley.BerkeleyStoreModule;
import org.junit.After;
import org.junit.Before;
import org.radix.GlobalInjector;
import org.radix.database.DatabaseEnvironment;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.messaging.MessageCentral;

import java.io.IOException;

import static org.mockito.Mockito.mock;

public class RadixTestWithStores extends RadixTest
{
	private DatabaseEnvironment dbEnv;
	private LedgerEntryStore store;
	private Tempo tempo;
	private MessageCentral messageCentral;
	private AddressBook addressBook;

	@Before
	public void beforeEachRadixTest() {
		this.dbEnv = new DatabaseEnvironment(getProperties());

		GlobalInjector injector = new GlobalInjector(getProperties(), dbEnv, getLocalSystem());
		this.messageCentral = injector.getInjector().getInstance(MessageCentral.class);
		this.addressBook = injector.getInjector().getInstance(AddressBook.class);

		EUID self = getLocalSystem().getNID();

		store = injector.getInjector().getInstance(LedgerEntryStore.class);
		tempo = new Tempo(
			mock(Application.class),
			ImmutableSet.of(),
			mock(LazyRequestDeliverer.class));
	}

	@After
	public void afterEachRadixTest() throws IOException {
		tempo.close();
		store.close();
		store.reset();
		messageCentral.close();
		addressBook.close();

		this.dbEnv.stop();
	}

	protected DatabaseEnvironment getDbEnv() {
		return dbEnv;
	}

	protected LedgerEntryStore getStore() {
		return store;
	}

	protected Tempo getTempo() {
		return tempo;
	}

	public MessageCentral getMessageCentral() {
		return messageCentral;
	}
}
