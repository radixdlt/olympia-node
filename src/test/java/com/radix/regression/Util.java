package com.radix.regression;

import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

public class Util {
	public static <T> Observer<T> loggingObserver(String name) {
		return new Observer<>() {
			@Override
			public void onSubscribe(Disposable d) {
				System.out.println(System.currentTimeMillis() + " " + name + ": <SUBSCRIBED>");
			}

			@Override
			public void onNext(T t) {
				System.out.println(System.currentTimeMillis() + " " + name + ": <NEXT> " + t);
			}

			@Override
			public void onError(Throwable e) {
				System.out.println(System.currentTimeMillis() + " " + name + ": <ERROR> " + e);
			}

			@Override
			public void onComplete() {
				System.out.println(System.currentTimeMillis() + " " + name + ": <COMPLETE>");
			}
		};
	}
}
