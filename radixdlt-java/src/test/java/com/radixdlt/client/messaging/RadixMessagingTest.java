package com.radixdlt.client.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.objects.UnencryptedData;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.ECSignature;
import com.radixdlt.client.core.identity.RadixIdentity;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class RadixMessagingTest {

	@Test
	public void testBadMessage() {
		ECPublicKey key = mock(ECPublicKey.class);
		RadixIdentity myIdentity = mock(RadixIdentity.class);
		when(myIdentity.getPublicKey()).thenReturn(key);
		TestObserver<RadixMessage> observer = TestObserver.create();

		Map<String, ECSignature> signatures = mock(Map.class);
		ECSignature signature = mock(ECSignature.class);
		when(signatures.get(any())).thenReturn(signature);
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("application", RadixMessaging.APPLICATION_ID);
		metaData.put("signatures", signatures);
		metaData.put("timestamp", 0L);

		UnencryptedData undecryptableData = mock(UnencryptedData.class);
		when(undecryptableData.getMetaData()).thenReturn(metaData);
		when(undecryptableData.getData()).thenReturn(new byte[] {0, 1, 2, 3});

		JsonObject message = new JsonObject();
		message.addProperty("from", "JHB89drvftPj6zVCNjnaijURk8D8AMFw4mVja19aoBGmRXWchnJ");
		message.addProperty("to", "JHB89drvftPj6zVCNjnaijURk8D8AMFw4mVja19aoBGmRXWchnJ");
		message.addProperty("content", "hello");
		UnencryptedData decryptableData = mock(UnencryptedData.class);
		when(decryptableData.getMetaData()).thenReturn(metaData);
		when(decryptableData.getData()).thenReturn(message.toString().getBytes());

		RadixApplicationAPI api = mock(RadixApplicationAPI.class);
		RadixAddress address = mock(RadixAddress.class);
		when(api.getAddress()).thenReturn(address);
		when(api.getIdentity()).thenReturn(myIdentity);
		when(api.getDecryptableData(any())).thenReturn(Observable.just(undecryptableData, decryptableData));

		RadixMessaging messaging = new RadixMessaging(api);
		messaging.getAllMessages()
			.subscribe(observer);

		observer.assertValueCount(1);
		observer.assertNoErrors();
	}
}
