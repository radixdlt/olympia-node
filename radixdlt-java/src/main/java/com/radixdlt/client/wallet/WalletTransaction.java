package com.radixdlt.client.wallet;

import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.AbstractConsumable;
import com.radixdlt.client.core.atoms.Particle;
import com.radixdlt.client.core.atoms.TransactionAtom;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class WalletTransaction {
	private final long balanceChange;
	private final TransactionAtom transactionAtom;

	public WalletTransaction(RadixAddress address, TransactionAtom transactionAtom) {
		this.balanceChange =
			transactionAtom.getParticles().stream()
				.filter(Particle::isAbstractConsumable)
				.map(Particle::getAsAbstractConsumable)
				.filter(particle -> particle.getAssetId().equals(Asset.XRD.getId()))
				.filter(particle -> particle.hasOwner(address.getPublicKey()))
				.mapToLong(AbstractConsumable::signedQuantity)
				.sum();

		this.transactionAtom = transactionAtom;
	}

	public TransactionAtom getTransactionAtom() {
		return transactionAtom;
	}

	public long getChange() {
		return balanceChange;
	}

	@Override
	public String toString() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault());
		sdf.setTimeZone(TimeZone.getDefault());

		return sdf.format(new Date(transactionAtom.getTimestamp())) + ": " +
				transactionAtom.summary() + (transactionAtom.getPayload() == null ? "" : " Payload: " + transactionAtom.getPayload().base64());
	}
}
