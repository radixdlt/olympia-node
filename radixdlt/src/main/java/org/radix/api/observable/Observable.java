package org.radix.api.observable;

import java.util.function.Consumer;

public interface Observable<T> {
	Disposable subscribe(Consumer<T> observer);
}
