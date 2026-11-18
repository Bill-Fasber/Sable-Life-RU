package dev.sablelife.compat.littletiles;

import java.util.concurrent.atomic.AtomicBoolean;

/** Per-cache loss flag and bounded retry budget, without optional dependency references. */
public final class MeshRecoveryState {
    private final AtomicBoolean lost = new AtomicBoolean();
    private boolean started;
    private long window;
    private int attempts;

    public void lost() { lost.set(true); }
    public void built() { lost.set(false); }
    public boolean hasLoss() { return lost.get(); }

    public synchronized boolean take(long now) {
        if (!lost.get()) return false;
        if (!started || now - window >= 5_000_000_000L) {
            started = true;
            window = now;
            attempts = 0;
        }
        if (attempts >= 2 || !lost.compareAndSet(true, false)) return false;
        attempts++;
        return true;
    }
}
