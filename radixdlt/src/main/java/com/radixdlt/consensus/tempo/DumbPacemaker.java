package com.radixdlt.consensus.tempo;

import java.util.concurrent.atomic.AtomicReference;

public class DumbPacemaker {
	private AtomicReference<Runnable> callbackRef;
	private Thread thread;

	public DumbPacemaker() {
		this.callbackRef = new AtomicReference<>();
	}

	public void start() {
		this.thread = new Thread(this::process, "Dumb Pacemaker");
		this.thread.start();
	}

	private void process() {
		while (true) {
			try {
				Thread.sleep(200);
				Runnable callback = callbackRef.get();
				if (callback != null) {
					callback.run();
				}
			} catch (InterruptedException e) {
			}
		}
	}

	public void addCallback(Runnable callback) {
		this.callbackRef.set(callback);
	}
}
