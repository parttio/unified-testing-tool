package org.vaadin.addons.unified.rewrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.openrewrite.HttpSenderExecutionContextView;
import org.openrewrite.ipc.http.HttpSender;
import org.openrewrite.test.RewriteTest;

/** The recipe tests' downloads wait and retry when Maven Central rate-limits them. */
class RetryingHttpSenderTest {

    private final List<Long> waits = new ArrayList<>();
    private final List<Integer> closed = new ArrayList<>();

    private HttpSender.Response response(int code, Map<String, List<String>> headers) {
        return new HttpSender.Response(code, new ByteArrayInputStream(new byte[0]), headers, () -> closed.add(code));
    }

    /** A sender that answers from the queue: a response, or an IOException to throw. */
    private HttpSender answering(Object... answers) {
        Deque<Object> queue = new LinkedList<>(List.of(answers));
        return request -> {
            Object next = queue.pop();
            if (next instanceof IOException e) {
                throw new UncheckedIOException(e);
            }
            return (HttpSender.Response) next;
        };
    }

    private HttpSender.Response get(HttpSender sender) {
        RetryingHttpSender retrying = new RetryingHttpSender(sender, waits::add);
        return retrying.send(retrying.get("https://repo.maven.apache.org/maven2/x.pom").build());
    }

    @Test
    void waitsAndRetriesARateLimitedDownload() {
        HttpSender.Response ok = response(200, Map.of());

        HttpSender.Response r = get(answering(response(429, Map.of()), response(429, Map.of()), ok));

        assertEquals(200, r.getCode());
        assertEquals(List.of(1000L, 2000L), waits);
        assertEquals(List.of(429, 429), closed);
    }

    @Test
    void waitsAsRetryAfterSays() {
        get(answering(response(429, Map.of("retry-after", List.of("3"))), response(200, Map.of())));

        assertEquals(List.of(3000L), waits);
    }

    @Test
    void givesTheLastAnswerAfterSixAttempts() {
        Object[] limited = new Object[RetryingHttpSender.ATTEMPTS];
        for (int i = 0; i < limited.length; i++) {
            limited[i] = response(429, Map.of());
        }

        assertEquals(429, get(answering(limited)).getCode());
        assertEquals(List.of(1000L, 2000L, 4000L, 8000L, 16000L), waits);
    }

    /** A rate limit that outlasts the retries: later downloads fail at once instead of each waiting again. */
    @Test
    void stopsWaitingOnceARequestRanOutOfAttempts() {
        AtomicBoolean gaveUp = new AtomicBoolean();
        Object[] limited = new Object[RetryingHttpSender.ATTEMPTS + 1];
        for (int i = 0; i < limited.length; i++) {
            limited[i] = response(429, Map.of());
        }
        HttpSender sender = answering(limited);
        RetryingHttpSender first = new RetryingHttpSender(sender, waits::add, gaveUp);
        RetryingHttpSender second = new RetryingHttpSender(sender, waits::add, gaveUp);

        assertEquals(429, first.send(first.get("https://repo.maven.apache.org/maven2/a.pom").build()).getCode());
        waits.clear();
        assertEquals(429, second.send(second.get("https://repo.maven.apache.org/maven2/b.pom").build()).getCode());

        assertEquals(List.of(), waits);
    }

    /** Once a download succeeds again, a 429 is waited for and retried again. */
    @Test
    void waitsAgainAfterADownloadSucceeds() {
        AtomicBoolean gaveUp = new AtomicBoolean(true);
        RetryingHttpSender sender = new RetryingHttpSender(
                answering(response(200, Map.of()), response(429, Map.of()), response(200, Map.of())), waits::add,
                gaveUp);

        assertEquals(200, sender.send(sender.get("https://repo.maven.apache.org/maven2/a.pom").build()).getCode());
        assertEquals(200, sender.send(sender.get("https://repo.maven.apache.org/maven2/b.pom").build()).getCode());

        assertEquals(List.of(1000L), waits);
    }

    /** A repository a POM names but the build doesn't need: its miss stays quick. */
    @Test
    void returnsOtherAnswersAndFailuresAtOnce() {
        assertEquals(404, get(answering(response(404, Map.of()))).getCode());
        assertEquals(503, get(answering(response(503, Map.of()))).getCode());
        assertThrows(UncheckedIOException.class, () -> get(answering(new IOException("refused"))));
        assertEquals(List.of(), waits);
    }

    @Test
    void ignoresARetryAfterDate() {
        assertEquals(java.util.Optional.empty(),
                RetryingHttpSender.retryAfter(Map.of("Retry-After", List.of("Wed, 21 Oct 2026 07:28:00 GMT"))));
    }

    /** The launcher session listener has put it in every recipe test's execution context. */
    @Test
    void everyRecipeTestUsesIt() {
        RewriteTest test = new RewriteTest() {};
        assertInstanceOf(RetryingHttpSender.class,
                HttpSenderExecutionContextView.view(test.defaultExecutionContext(new org.openrewrite.test.SourceSpec<?>[0]))
                        .getHttpSender());
    }
}
