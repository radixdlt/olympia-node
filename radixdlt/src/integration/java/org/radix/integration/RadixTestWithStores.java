package org.radix.integration;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.EUID;
import com.radixdlt.consensus.tempo.Application;
import com.radixdlt.consensus.tempo.Scheduler;
import com.radixdlt.consensus.tempo.Tempo;
import com.radixdlt.delivery.LazyRequestDeliverer;
import com.radixdlt.delivery.LazyRequestDelivererConfiguration;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.store.LedgerEntryStoreView;
import org.junit.After;
import org.junit.Before;
import org.radix.GlobalInjector;
import org.radix.database.DatabaseEnvironment;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.messaging.MessageCentral;

import java.io.IOException;
import java.util.Objects;

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

		GlobalInjector injector = new GlobalInjector(getProperties(), dbEnv, getLocalSystem(), getUniverse());
		this.messageCentral = injector.getInjector().getInstance(MessageCentral.class);
		this.addressBook = injector.getInjector().getInstance(AddressBook.class);

		EUID self = getLocalSystem().getNID();

		store = injector.getInjector().getInstance(LedgerEntryStore.class);
		tempo = new Tempo(
			mock(Application.class),
			ImmutableSet.of(),
			new LazyRequestDeliverer(
				mock(Scheduler.class),
				mock(MessageCentral.class),
				mock(LedgerEntryStoreView.class),
				LazyRequestDelivererConfiguration.fromRuntimeProperties(getProperties()),
				getUniverse()
			));
	}

	@After
	public void afterEachRadixTest() throws IOException {
		tempo.close();
		store.close();
		store.reset();
		messageCentral.close();
		addressBook.close();

		this.dbEnv.stop();

		dbEnv = null;
		store = null;
		tempo = null;
		messageCentral = null;
		addressBook = null;
	}

	protected DatabaseEnvironment getDbEnv() {
		return Objects.requireNonNull(dbEnv, "dbEnv was not initialized");
	}

	protected LedgerEntryStore getStore() {
		return Objects.requireNonNull(store, "store was not initialized");
	}

	protected Tempo getTempo() {
		return Objects.requireNonNull(tempo, "tempo was not initialized");
	}

	public MessageCentral getMessageCentral() {
		return Objects.requireNonNull(messageCentral, "messageCentral was not initialized");
	}
}
