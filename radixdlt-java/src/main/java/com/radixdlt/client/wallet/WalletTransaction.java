package com.radixdlt.client.wallet;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.AbstractConsumable;
import com.radixdlt.client.core.atoms.Particle;
import com.radixdlt.client.core.atoms.TransactionAtom;

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
