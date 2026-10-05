package ru.eyeone.relaybridge;

import org.junit.Test;
import static org.junit.Assert.*;

public class ListenerRebindTest {
    @Test public void stableConnectionIsReboundImmediately() {
        assertEquals(0, RelayListener.nextRebindDelay(60_000, 0));
        assertEquals(0, RelayListener.nextRebindDelay(3_600_000, 300_000));
    }

    @Test public void repeatedQuickDisconnectsBackOffUpToFiveMinutes() {
        long delay = 0;
        long[] expected = {5_000, 10_000, 20_000, 40_000, 80_000, 160_000, 300_000, 300_000};
        for (long step : expected) {
            delay = RelayListener.nextRebindDelay(1_000, delay);
            assertEquals(step, delay);
        }
    }

    @Test public void backOffStartsAgainAfterAStableConnection() {
        long delay = RelayListener.nextRebindDelay(1_000, 160_000);
        assertEquals(300_000, delay);
        delay = RelayListener.nextRebindDelay(120_000, delay);
        assertEquals(0, delay);
        assertEquals(5_000, RelayListener.nextRebindDelay(1_000, delay));
    }
}
