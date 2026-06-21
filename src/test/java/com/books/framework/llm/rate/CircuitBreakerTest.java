package com.books.framework.llm.rate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerTest {

    @Test
    void allowsRequestsWhenClosed() {
        CircuitBreaker cb = new CircuitBreaker(3, 60);
        assertTrue(cb.allowsRequest("a"));
        assertTrue(cb.allowsRequest("a"));
    }

    @Test
    void opensAfterThresholdFailures() {
        CircuitBreaker cb = new CircuitBreaker(2, 60);
        cb.recordFailure("b");
        assertTrue(cb.allowsRequest("b")); // still closed
        cb.recordFailure("b");
        assertFalse(cb.allowsRequest("b")); // now open
    }

    @Test
    void successResetsFailureCount() {
        CircuitBreaker cb = new CircuitBreaker(2, 60);
        cb.recordFailure("c");
        cb.recordSuccess("c");
        cb.recordFailure("c");
        assertTrue(cb.allowsRequest("c")); // still closed, count=1
    }

    @Test
    void goesToHalfOpenAfterRecovery() {
        CircuitBreaker cb = new CircuitBreaker(1, 1); // 1s recovery
        cb.recordFailure("d");
        assertFalse(cb.allowsRequest("d")); // open
        sleep(1100); // wait for recovery
        assertTrue(cb.allowsRequest("d")); // half-open
    }

    @Test
    void halfOpenSuccessClosesCircuit() {
        CircuitBreaker cb = new CircuitBreaker(1, 1, 2);
        cb.recordFailure("e");
        assertFalse(cb.allowsRequest("e"));
        sleep(1100);
        assertTrue(cb.allowsRequest("e")); // half-open probe
        cb.recordSuccess("e");
        assertTrue(cb.allowsRequest("e")); // closed again
    }

    @Test
    void halfOpenFailureReopensCircuit() {
        CircuitBreaker cb = new CircuitBreaker(1, 1, 2);
        cb.recordFailure("f");
        assertFalse(cb.allowsRequest("f"));
        sleep(1100);
        assertTrue(cb.allowsRequest("f")); // half-open probe #1
        cb.recordFailure("f"); // fails in half-open
        assertFalse(cb.allowsRequest("f")); // back to open
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @Test
    void independentProviders() {
        CircuitBreaker cb = new CircuitBreaker(2, 60);
        cb.recordFailure("i");
        cb.recordFailure("i");
        assertFalse(cb.allowsRequest("i"));
        assertTrue(cb.allowsRequest("j")); // different provider, still closed
    }
}
