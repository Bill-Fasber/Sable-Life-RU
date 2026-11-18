package dev.sablelife.compat.littletiles;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

final class MeshRecoveryStateTest {
    @Test void oneLossIsClaimedOnceAndNormalRebuildClearsIt() {
        var state = new MeshRecoveryState();
        assertFalse(state.take(1));
        state.lost();
        assertTrue(state.take(1));
        assertFalse(state.hasLoss());
        assertFalse(state.take(2));
        state.lost(); state.built();
        assertFalse(state.take(3));
    }

    @Test void rebuildDoesNotResetTheBoundedRetryBudget() {
        var state = new MeshRecoveryState();
        state.lost(); assertTrue(state.take(10));
        state.built(); state.lost(); assertTrue(state.take(11));
        state.built(); state.lost(); assertFalse(state.take(12));
        assertTrue(state.hasLoss());
        assertFalse(state.take(5_000_000_009L));
        assertTrue(state.take(5_000_000_010L));
    }

    @Test void arbitraryNanoTimeOriginIncludingZeroIsValid() {
        for (long origin : new long[]{0, -10_000_000_000L, Long.MAX_VALUE - 2}) {
            var state = new MeshRecoveryState();
            state.lost(); assertTrue(state.take(origin));
            state.lost(); assertTrue(state.take(origin + 1));
            state.lost(); assertFalse(state.take(origin + 2));
            assertTrue(state.take(origin + 5_000_000_000L));
        }
    }

    @Test void concurrentWorkersCannotClaimTheSameLossTwice() throws Exception {
        var state = new MeshRecoveryState(); state.lost();
        var claimed = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(8)) {
            var gate = new CountDownLatch(1);
            var work = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < 16; i++) work.add(pool.submit(() -> {
                try { gate.await(); } catch (InterruptedException e) { throw new RuntimeException(e); }
                if (state.take(1)) claimed.incrementAndGet();
            }));
            gate.countDown(); for (var task : work) task.get();
        }
        assertEquals(1, claimed.get());
    }
}
