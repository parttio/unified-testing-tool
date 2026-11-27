package org.vaadin.addons.unified.rewrite;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongConsumer;
import org.openrewrite.ipc.http.HttpSender;

/**
 * Sends OpenRewrite's requests (the POMs a build test resolves) again when a repository says "too many
 * requests" (HTTP 429). Waits as {@code Retry-After} says, else 1, 2, 4, 8, 16 s; gives the last answer after
 * {@value #ATTEMPTS} attempts. Any other answer, and a connection that fails, goes back at once: OpenRewrite tries
 * every repository a POM names, and a miss in one it doesn't need must stay quick.
 * <p>
 * The build tests resolve the POMs of the dependencies the recipes add from Maven Central: OpenRewrite reads a
 * POM from the local repository only when the artifact's plain jar is next to it, which a shaded-only artifact
 * ({@code flow-build-tools}) never has. A busy CI runner then gets HTTP 429.
 * <p>
 * Once a request is still rate-limited after its last attempt, the senders stop waiting: until a download
 * succeeds again, a 429 goes back at once. A rate limit that outlasts the retries otherwise makes every later
 * download wait half a minute, and the build tests hang until the CI job times out instead of failing.
 */
final class RetryingHttpSender implements HttpSender {

    static final int ATTEMPTS = 6;
    private static final long MAX_WAIT_MILLIS = 30_000;

    /** Shared by the senders of one test run: each recipe test's execution context gets a sender of its own. */
    private static final AtomicBoolean GAVE_UP = new AtomicBoolean();

    private final HttpSender delegate;
    private final LongConsumer sleep;
    private final AtomicBoolean gaveUp;

    RetryingHttpSender(HttpSender delegate) {
        this(delegate, RetryingHttpSender::sleep, GAVE_UP);
    }

    RetryingHttpSender(HttpSender delegate, LongConsumer sleep) {
        this(delegate, sleep, new AtomicBoolean());
    }

    RetryingHttpSender(HttpSender delegate, LongConsumer sleep, AtomicBoolean gaveUp) {
        this.delegate = delegate;
        this.sleep = sleep;
        this.gaveUp = gaveUp;
    }

    @Override
    public Response send(Request request) {
        for (int attempt = 1;; attempt++) {
            Response response = delegate.send(request);
            int code = response.getCode();
            if (code >= 200 && code < 300) {
                gaveUp.set(false);
            }
            if (code != 429 || gaveUp.get()) {
                return response;
            }
            if (attempt == ATTEMPTS) {
                gaveUp.set(true);
                return response;
            }
            long wait = retryAfter(response.getHeaders()).orElse(backoff(attempt));
            response.close();
            sleep.accept(wait);
        }
    }

    /** 1 s, 2 s, 4 s, ... after the n-th attempt. */
    static long backoff(int attempt) {
        return Math.min(MAX_WAIT_MILLIS, 1000L << (attempt - 1));
    }

    /** {@code Retry-After} in seconds, at most {@value #MAX_WAIT_MILLIS} ms; a date or garbage is ignored. */
    static Optional<Long> retryAfter(Map<String, List<String>> headers) {
        if (headers == null) {
            return Optional.empty();
        }
        return headers.entrySet().stream()
                .filter(h -> h.getKey() != null && h.getKey().equalsIgnoreCase("Retry-After"))
                .flatMap(h -> h.getValue().stream()).findFirst().flatMap(v -> {
                    try {
                        return Optional.of(Math.min(MAX_WAIT_MILLIS, Long.parseLong(v.trim()) * 1000));
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                });
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry", e);
        }
    }
}
