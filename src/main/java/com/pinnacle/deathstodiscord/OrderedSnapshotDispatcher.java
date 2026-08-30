package com.pinnacle.deathstodiscord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Dispatches completed snapshot builds in the order in which they started. */
final class OrderedSnapshotDispatcher {

    private final Map<Long, Runnable> readyDispatches = new HashMap<>();
    private long epoch;
    private long nextReservation;
    private long nextDispatch;

    synchronized Reservation reserve() {
        return new Reservation(epoch, nextReservation++);
    }

    boolean complete(Reservation reservation, Runnable dispatch) {
        List<Runnable> newlyOrdered = new ArrayList<>();
        synchronized (this) {
            if (reservation.epoch() != epoch || reservation.sequence() < nextDispatch) {
                return false;
            }
            if (readyDispatches.putIfAbsent(reservation.sequence(), dispatch) != null) {
                throw new IllegalStateException("Snapshot reservation completed more than once.");
            }

            Runnable next;
            while ((next = readyDispatches.remove(nextDispatch)) != null) {
                newlyOrdered.add(next);
                nextDispatch++;
            }
        }

        newlyOrdered.forEach(Runnable::run);
        return true;
    }

    synchronized void reset() {
        epoch++;
        nextReservation = 0L;
        nextDispatch = 0L;
        readyDispatches.clear();
    }

    record Reservation(long epoch, long sequence) {
    }
}
