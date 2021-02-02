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

package com.radixdlt.store.mvstore;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Scopes;
import com.radixdlt.CryptoModule;
import com.radixdlt.MVStorePersistenceModule;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.store.LedgerEntry;
import com.radixdlt.store.LedgerEntryGenerator;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.store.NextCommittedLimitReachedException;
import org.apache.commons.cli.ParseException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class GetNextCommittedLedgerEntriesTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private Injector injector;
	private LedgerEntryGenerator ledgerEntryGenerator = new LedgerEntryGenerator();

	@Before
	public void setup() {
		this.injector = Guice.createInjector(
			new CryptoModule(),
			new MVStorePersistenceModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					final RuntimeProperties runtimeProperties;
					try {
						runtimeProperties = new RuntimeProperties(new JSONObject(), new String[0]);
						runtimeProperties.set(
							"db.location",
							folder.getRoot().getAbsolutePath() + "/RADIXDB_TEST"
						);
					} catch (ParseException e) {
						throw new IllegalStateException();
					}
					bind(RuntimeProperties.class).toInstance(runtimeProperties);
					bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
				}
			}
		);
	}

	@After
	public void teardown() {
		if (this.injector != null) {
			this.injector.getInstance(LedgerEntryStore.class).close();
			this.injector.getInstance(PersistentSafetyStateStore.class).close();
			this.injector.getInstance(DatabaseEnvironment.class).stop();
		}
	}

	@Test
	public void database_smaller_than_limit__returns_smaller_than_limit() throws NextCommittedLimitReachedException {
		final var ledgerEntryStore = this.injector.getInstance(LedgerEntryStore.class);

		generateLedgerEntries(ledgerEntryStore, 1, 1);

		final var entries = ledgerEntryStore.getNextCommittedLedgerEntries(-1, 10);
		assertThat(entries)
			.hasSize(1)
			.element(0)
			.extracting(LedgerEntry::getProofVersion)
			.isEqualTo(0L);
	}

	@Test
	public void commits_with_1_entry__limit_1_returns_single_entry() throws NextCommittedLimitReachedException {
		final var ledgerEntryStore = this.injector.getInstance(LedgerEntryStore.class);

		generateLedgerEntries(ledgerEntryStore, 50, 1);

		final var entries = ledgerEntryStore.getNextCommittedLedgerEntries(-1, 1);
		assertThat(entries)
			.hasSize(1)
			.element(0)
			.extracting(LedgerEntry::getProofVersion)
			.isEqualTo(0L);
	}

	@Test
	public void commits_with_10_entries__limit_10_returns_10_entries() throws NextCommittedLimitReachedException {
		final var ledgerEntryStore = this.injector.getInstance(LedgerEntryStore.class);

		generateLedgerEntries(ledgerEntryStore, 50, 10);

		final var entries = ledgerEntryStore.getNextCommittedLedgerEntries(-1, 10);
		assertThat(entries)
			.hasSize(10)
			.extracting(LedgerEntry::getProofVersion)
			.allMatch(v -> v == 0L);
	}

	@Test
	public void commits_with_10_entries__limit_15_returns_10_entries() throws NextCommittedLimitReachedException {
		final var ledgerEntryStore = this.injector.getInstance(LedgerEntryStore.class);

		generateLedgerEntries(ledgerEntryStore, 50, 10);

		final var entries = ledgerEntryStore.getNextCommittedLedgerEntries(-1, 15);
		assertThat(entries)
			.hasSize(10)
			.extracting(LedgerEntry::getProofVersion)
			.allMatch(v -> v == 0L);
	}

	@Test
	public void commits_with_10_entries__limit_20_returns_20_entries() throws NextCommittedLimitReachedException {
		final var ledgerEntryStore = this.injector.getInstance(LedgerEntryStore.class);

		generateLedgerEntries(ledgerEntryStore, 50, 10);

		final var entries = ledgerEntryStore.getNextCommittedLedgerEntries(-1, 20);
		assertThat(entries)
			.hasSize(20)
			.extracting(LedgerEntry::getProofVersion)
			.containsExactlyElementsOf(LongStream.range(0, 20).map(v -> v / 10).boxed().collect(Collectors.toList()));
	}

	@Test
	public void commits_with_10_entries__limit_5_throws_exception() {
		final var ledgerEntryStore = this.injector.getInstance(LedgerEntryStore.class);

		generateLedgerEntries(ledgerEntryStore, 50, 10);

		assertThatThrownBy(() -> ledgerEntryStore.getNextCommittedLedgerEntries(-1, 5))
			.isInstanceOf(RuntimeException.class)
			.hasCauseExactlyInstanceOf(NextCommittedLimitReachedException.class);
	}

	private void generateLedgerEntries(LedgerEntryStore ledgerEntryStore, int numCommits, int commitSize) {
		final var entries = Lists.<LedgerEntry>newArrayList();
		for (int i = 0; i < numCommits; ++i) {
			entries.addAll(this.ledgerEntryGenerator.createLedgerEntriesBatch(commitSize));
		}
		assertThat(entries.size()).isEqualTo(numCommits * commitSize);

		final var tx = ledgerEntryStore.createTransaction();
		for (final var entry : entries) {
			final var result = ledgerEntryStore.store(tx, entry, ImmutableSet.of(), ImmutableSet.of());
			assertThat(result.isSuccess()).isTrue();
		}
		tx.commit();
	}
}
