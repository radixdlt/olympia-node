package com.radixdlt.client.core.network;

import com.radixdlt.client.core.network.reducers.RadixNetworkState;
import io.reactivex.Observable;

public interface RadixNetworkEpic {
	Observable<RadixNodeAction> epic(Observable<RadixNodeAction> update, Observable<RadixNetworkState> networkState);
}
