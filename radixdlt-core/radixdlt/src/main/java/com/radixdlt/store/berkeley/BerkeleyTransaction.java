package com.radixdlt.store.berkeley;

import com.radixdlt.store.Transaction;

public class BerkeleyTransaction implements Transaction {
	private final com.sleepycat.je.Transaction transaction;

	private BerkeleyTransaction(com.sleepycat.je.Transaction transaction) {
		this.transaction = transaction;
	}

	public static Transaction wrap(com.sleepycat.je.Transaction transaction) {
		return new BerkeleyTransaction(transaction);
	}

	@Override
	public void commit() {
		transaction.commit();
	}

	@Override
	public void abort() {
		transaction.abort();
	}

	@Override
	public com.sleepycat.je.Transaction unwrap() {
		return transaction;
	}
}
