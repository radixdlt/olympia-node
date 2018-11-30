package com.radix.regression;

import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

public class Util {
	static <T> Observer<T> loggingObserver(String name) {
		return new Observer<T>() {
			@Override
			public void onSubscribe(Disposable d) {
				System.out.println(name + ": <SUBSCRIBED>");
			}

			@Override
			public void onNext(T t) {
				System.out.println(name + ": <RECEIVED> " + t);
			}

			@Override
			public void onError(Throwable e) {
				System.out.println(name + ": <ERROR> " + e);
			}

			@Override
			public void onComplete() {
				System.out.println(name + ": <COMPLETE>");
			}
		};
	}
}
