package com.radixdlt.test;

import io.reactivex.Observable;

public interface RemoteBFTCheck {
	Observable<Object> check(DockerBFTTestNetwork network);
}
