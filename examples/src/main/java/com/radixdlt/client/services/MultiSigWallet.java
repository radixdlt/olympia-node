package com.radixdlt.client.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.IdParticle;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.identity.RadixIdentity;
import com.radixdlt.client.core.identity.SimpleRadixIdentity;
import com.radixdlt.client.messaging.RadixMessage;
import com.radixdlt.client.messaging.RadixMessaging;
import com.radixdlt.client.wallet.RadixWallet;
import io.reactivex.Observable;
import io.reactivex.observables.ConnectableObservable;
import java.io.IOException;
import java.nio.ByteBuffer;

public class MultiSigWallet {

	private final static JsonDeserializer<RadixAddress> addressDeserializer = (json, typeOf, context) -> new RadixAddress(json.getAsString());
	private final static JsonSerializer<RadixAddress> addressSerializer = (src, typeOf, context) -> new JsonPrimitive(src.toString());

	private final static Gson gson = new GsonBuilder()
		.registerTypeAdapter(RadixAddress.class, addressDeserializer)
		.registerTypeAdapter(RadixAddress.class, addressSerializer)
		.create();

	public static class SignedRequest {
		private final RadixAddress to;
		private final long amount;
		private final long nonce;
		private final EUID euid;

		public SignedRequest(RadixAddress to, long amount, long nonce) {
			this.to = to;
			this.amount = amount;
			this.nonce = nonce;

			byte[] pub = to.getPublicKey().toByteArray();
			byte[] unique = ByteBuffer
				.allocate(pub.length + 8 + 8)
				.put(pub)
				.putLong(amount)
				.putLong(nonce)
				.array()
				;

			this.euid = RadixHash.of(unique).toEUID();
		}

		public String toJson() {
			return gson.toJson(this);
		}

		public EUID hash() {
			return euid;
		}

		@Override
		public int hashCode() {
			return euid.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			SignedRequest msg = (SignedRequest)o;
			return euid.equals(msg.euid);
		}
	}

	private final RadixIdentity radixIdentity;
	private final ECPublicKey keyA;
	private final ECPublicKey keyB;
	private final Observable<RadixMessage> allTransactions;

	private MultiSigWallet(RadixIdentity radixIdentity, ECPublicKey keyA, ECPublicKey keyB) {
		this.radixIdentity = radixIdentity;
		this.keyA = keyA;
		this.keyB = keyB;

		this.allTransactions = RadixMessaging.getInstance()
			.getAllMessagesDecrypted(radixIdentity)
			.filter(RadixMessage::validateSignature)
			.publish()
			.autoConnect(2)
		;
	}

	private Observable<SignedRequest> signedReqsFrom(ECPublicKey key) {
		return this.allTransactions.filter(txReq -> txReq.getFrom().getPublicKey().equals(key))
			.map(txReq -> gson.fromJson(txReq.getContent(), SignedRequest.class))
			.doOnNext(txReq -> System.out.println("Tx " + txReq.hash() + " Signed By " + key));
	}

	private IdParticle uniqueId(EUID euid) {
		return IdParticle.create("multi-sig", euid, radixIdentity.getPublicKey());
	}

	public void run() {
		RadixAddress address = RadixUniverse.getInstance().getAddressFrom(radixIdentity.getPublicKey());
		RadixWallet wallet = RadixWallet.getInstance();

		System.out.println("MultiSig Address: " + address);

		wallet.getSubUnitBalance(address, Asset.XRD)
			.subscribe(balance -> System.out.println("Balance: " + balance));

		RadixMessaging.getInstance()
			.getAllMessagesDecrypted(radixIdentity)
			.filter(RadixMessage::validateSignature)
			.subscribe(System.out::println);

		Observable<SignedRequest> signedFromA = signedReqsFrom(keyA);
		ConnectableObservable<SignedRequest> signedFromB = signedReqsFrom(keyB).replay();

		signedFromA
			.flatMapSingle(txA -> signedFromB.filter(txB -> txA.hash().equals(txB.hash())).firstOrError())
			.doOnSubscribe(x -> signedFromB.connect())
			.subscribe(tx -> wallet.transferXRD(tx.amount, radixIdentity, tx.to, uniqueId(tx.hash())))
		;
	}

	public static void main(String[] args) throws IOException {

		if (args.length < 2) {
			System.out.println("Usage: java com.radixdlt.client.services.MultiSig <highgarden|sunstone|winterfell|winterfell_local> <keyfile>");
			System.exit(-1);
		}

		RadixUniverse.bootstrap(Bootstrap.valueOf(args[0].toUpperCase()));

		RadixUniverse.getInstance()
			.getNetwork()
			.getStatusUpdates()
			.subscribe(System.out::println);

		final RadixIdentity multiSigIdentity = new SimpleRadixIdentity(args[1]);
		final RadixIdentity person1 = new SimpleRadixIdentity("1.key");
		final RadixIdentity person2 = new SimpleRadixIdentity("2.key");

		MultiSigWallet multiSigWallet = new MultiSigWallet(multiSigIdentity, person1.getPublicKey(), person2.getPublicKey());
		multiSigWallet.run();
	}
}
