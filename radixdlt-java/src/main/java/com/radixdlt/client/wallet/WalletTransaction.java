package com.radixdlt.client.wallet;

import com.radixdlt.client.core.crypto.ECPublicKey;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.AbstractConsumable;
import com.radixdlt.client.core.atoms.Particle;
import com.radixdlt.client.core.atoms.TransactionAtom;
import java.util.stream.Collectors;

/**
 * A transaction from the perspective of a wallet. Amount is positive or
 * negative depending on how it affects the balance of the wallet.
 */
public class WalletTransaction {
	private final long amount;
	private final TransactionAtom transactionAtom;

	public WalletTransaction(RadixAddress address, TransactionAtom transactionAtom) {
		this.amount =
			transactionAtom.getParticles().stream()
				.filter(Particle::isAbstractConsumable)
				.map(Particle::getAsAbstractConsumable)
				.filter(particle -> particle.getAssetId().equals(Asset.XRD.getId()))
				.filter(particle -> particle.getOwnersPublicKeys().stream().allMatch(address::ownsKey))
				.mapToLong(AbstractConsumable::getSignedQuantity)
				.sum();

		this.transactionAtom = transactionAtom;
	}

	public TransactionAtom getTransactionAtom() {
		return transactionAtom;
	}

	public Set<ECPublicKey> getSenders() {
		return transactionAtom.summary().entrySet().stream()
			.filter(entry -> entry.getValue().containsKey(Asset.XRD.getId()))
			.filter(entry -> entry.getValue().get(Asset.XRD.getId()) < 0)
			.flatMap(entry -> entry.getKey().stream())
			.collect(Collectors.toSet());
	}

	public long getAmount() {
		return amount;
	}

	@Override
	public String toString() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault());
		sdf.setTimeZone(TimeZone.getDefault());

		StringBuilder txString = new StringBuilder(sdf.format(new Date(transactionAtom.getTimestamp()))
			+ ": " + transactionAtom.summary());

		if (transactionAtom.getPayload() != null) {
			txString.append(" Payload: " + transactionAtom.getPayload().base64());
		}

		return txString.toString();
	}
}
