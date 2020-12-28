package Network;

import java.util.concurrent.atomic.AtomicLong;

public class MessageCounter {
    private final AtomicLong counter = new AtomicLong(0);

    public long getCounter() {
        return counter.incrementAndGet();
    }
}