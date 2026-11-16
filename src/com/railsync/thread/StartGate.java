package com.railsync.thread;

/**
 * A starting gate built on {@code wait()} / {@code notifyAll()} (inter-thread communication).
 * Worker threads call {@link #await()} and block in the WAITING state; the controller calls
 * {@link #open()} once, which wakes all of them at the same moment so they really compete.
 */
public class StartGate {
    private boolean open = false;

    public synchronized void await() throws InterruptedException {
        while (!open) {          // loop guards against spurious wake-ups
            wait();
        }
    }

    public synchronized void open() {
        open = true;
        notifyAll();
    }
}
