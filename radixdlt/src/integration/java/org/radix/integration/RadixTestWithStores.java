package org.radix.integration;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.tempo.Application;
import com.radixdlt.consensus.tempo.Scheduler;
import com.radixdlt.consensus.tempo.Tempo;
import com.radixdlt.delivery.LazyRequestDeliverer;
import com.radixdlt.delivery.LazyRequestDelivererConfiguration;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.store.LedgerEntryStoreView;
import org.junit.After;
import org.junit.Before;
import org.mockito.invocation.InvocationOnMock;
import org.radix.GlobalInjector;
import org.radix.database.DatabaseEnvironment;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.messaging.MessageCentral;

import java.io.IOException;
import java.util.Objects;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RadixTestWithStores extends RadixTest
{
	private DatabaseEnvironment dbEnv;
	private LedgerEntryStore store;
	private Tempo tempo;
	private MessageCentral messageCentral;
	private AddressBook addressBook;

	@Before
	public void beforeEachRadixTest() throws InterruptedException {
		this.dbEnv = new DatabaseEnvironment(getProperties());

		GlobalInjector injector = new GlobalInjector(getProperties(), dbEnv, getLocalSystem(), getUniverse());
		this.messageCentral = injector.getInjector().getInstance(MessageCentral.class);
		this.addressBook = injector.getInjector().getInstance(AddressBook.class);

		Application deadApplication = mock(Application.class);
		when(deadApplication.takeNextEntry()).then(this::sleepForever);

		store = injector.getInjector().getInstance(LedgerEntryStore.class);
		tempo = new Tempo(
			deadApplication,
			ImmutableSet.of(),
			new LazyRequestDeliverer(
				mock(Scheduler.class),
				mock(MessageCentral.class),
				mock(LedgerEntryStoreView.class),
				LazyRequestDelivererConfiguration.fromRuntimeProperties(getProperties()),
				getUniverse()
			));
	}

	private <T> T sleepForever(InvocationOnMock invocation) throws InterruptedException {
		Thread.sleep(Long.MAX_VALUE);
		return null;
	}

	@After
	public void afterEachRadixTest() throws IOException {
		// Null checks to better handle case where @Before throws
		if (tempo != null) {
			tempo.close();
		}
		if (store != null) {
			store.close();
			store.reset();
		}
		if (messageCentral != null) {
			messageCentral.close();
		}
		if (addressBook != null) {
			addressBook.close();
		}

		if (this.dbEnv != null) {
			this.dbEnv.stop();
		}

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
