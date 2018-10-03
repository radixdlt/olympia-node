package com.radixdlt.client.core.ledger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.atoms.Consumable;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import java.util.Collection;
import java.util.function.Function;
import org.junit.Test;

public class ConsumableDataSourceTest {

	@Test
	public void testEmptyConsumables() {
		Function<EUID, Observable<AtomObservation>> atomStore = mock(Function.class);
		AtomObservation observation = mock(AtomObservation.class);
		when(observation.isHead()).thenReturn(true);
		RadixAddress address = mock(RadixAddress.class);
		when(atomStore.apply(any())).thenReturn(Observable.just(observation));
		ConsumableDataSource consumableDataSource = new ConsumableDataSource(atomStore);
		TestObserver<Collection<Consumable>> testObserver = TestObserver.create();
		consumableDataSource.getConsumables(address).subscribe(testObserver);
		testObserver.assertValue(Collection::isEmpty);
	}
}