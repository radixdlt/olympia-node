package com.radixdlt.client.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import com.radixdlt.client.application.EncryptedData;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.crypto.CryptoException;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.ECSignature;
import com.radixdlt.client.core.identity.RadixIdentity;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class RadixMessagingTest {

	@Test
	public void testReceiveUndecryptableMessage() {
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

		EncryptedData undecryptableData = mock(EncryptedData.class);
		when(undecryptableData.getMetaData()).thenReturn(metaData);

		EncryptedData decryptableData = mock(EncryptedData.class);
		when(decryptableData.getMetaData()).thenReturn(metaData);

		when(myIdentity.decrypt(any(EncryptedData.class)))
			.thenReturn(Single.error(new CryptoException("Can't decrypt")))
			.thenReturn(Single.fromCallable(() -> {
				JsonObject message = new JsonObject();
				message.addProperty("from", "JHB89drvftPj6zVCNjnaijURk8D8AMFw4mVja19aoBGmRXWchnJ");
				message.addProperty("to", "JHB89drvftPj6zVCNjnaijURk8D8AMFw4mVja19aoBGmRXWchnJ");
				message.addProperty("content", "hello");
				return message.toString().getBytes();
			}));

		RadixApplicationAPI api = mock(RadixApplicationAPI.class);

		RadixAddress address = mock(RadixAddress.class);
		when(api.getAddress()).thenReturn(address);
		when(api.getIdentity()).thenReturn(myIdentity);
		when(api.getEncryptedData(any())).thenReturn(Observable.just(undecryptableData, decryptableData));

		RadixMessaging messaging = new RadixMessaging(api);
		messaging.getAllMessages()
			.subscribe(observer);

		observer.assertValueCount(1);
		observer.assertNoErrors();
	}

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

		EncryptedData undecryptableData = mock(EncryptedData.class);
		when(undecryptableData.getMetaData()).thenReturn(metaData);

		EncryptedData decryptableData = mock(EncryptedData.class);
		when(decryptableData.getMetaData()).thenReturn(metaData);

		when(myIdentity.decrypt(any(EncryptedData.class)))
			.thenReturn(Single.just(new byte[] {0, 1, 2, 3}))
			.thenReturn(Single.fromCallable(() -> {
				JsonObject message = new JsonObject();
				message.addProperty("from", "JHB89drvftPj6zVCNjnaijURk8D8AMFw4mVja19aoBGmRXWchnJ");
				message.addProperty("to", "JHB89drvftPj6zVCNjnaijURk8D8AMFw4mVja19aoBGmRXWchnJ");
				message.addProperty("content", "hello");
				return message.toString().getBytes();
			}));

		RadixApplicationAPI api = mock(RadixApplicationAPI.class);

		RadixAddress address = mock(RadixAddress.class);
		when(api.getAddress()).thenReturn(address);
		when(api.getIdentity()).thenReturn(myIdentity);
		when(api.getEncryptedData(any())).thenReturn(Observable.just(undecryptableData, decryptableData));

		RadixMessaging messaging = new RadixMessaging(api);
		messaging.getAllMessages()
			.subscribe(observer);

		observer.assertValueCount(1);
		observer.assertNoErrors();
	}
}
