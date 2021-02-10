package com.radixdlt.environment.rx;

import com.google.inject.Inject;
import com.google.inject.Provider;
import io.reactivex.rxjava3.core.Observable;

import java.util.Objects;

public final class ObservableProvider<T> implements Provider<Observable<T>> {
    @Inject
    private Provider<RxEnvironment> rxEnvironmentProvider;
    private final Class<T> c;

    ObservableProvider(Class<T> c) {
        this.c = Objects.requireNonNull(c);
    }

    @Override
    public Observable<T> get() {
        return rxEnvironmentProvider.get().getObservable(c);
    }
}
