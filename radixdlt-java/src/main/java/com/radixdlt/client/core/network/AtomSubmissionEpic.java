package com.radixdlt.client.core.network;

import io.reactivex.Observable;

public interface AtomSubmissionEpic {
	Observable<AtomSubmissionUpdate> epic(Observable<AtomSubmissionUpdate> update, Observable<RadixNetworkState> networkState);
}
