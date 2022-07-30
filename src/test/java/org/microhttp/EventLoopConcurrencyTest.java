package org.microhttp;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;

public class EventLoopConcurrencyTest {

    static ExecutorService executor;
    static int port;
    static EventLoop eventLoop;

    @BeforeAll
    static void beforeAll() throws IOException {
        executor = Executors.newFixedThreadPool(1);
        Options options = new Options()
                .withPort(0)
                .withRequestTimeout(Duration.ofMillis(2_500))
                .withReadBufferSize(1_024)
                .withMaxRequestSize(2_048);
        Handler handler = (meta, req, callback) -> executor.execute(() -> {
            Response response = new Response(
                    200,
                    "OK",
                    List.of(new Header("Content-Type", req.header("Content-Type"))),
                    req.body());
            callback.accept(response);
        });
        eventLoop = new EventLoop(options, new DisabledLogger(), handler);
        eventLoop.start();
        port = eventLoop.getPort();
    }

    @AfterAll
    static void afterAll() throws InterruptedException {
        eventLoop.stop();
        eventLoop.join();
        executor.shutdown();
    }

    @ParameterizedTest
    @MethodSource("argsProvider")
    void concurrentRequests(int concurrency, int requestCount) throws InterruptedException, ExecutionException {
        Semaphore semaphore = new Semaphore(concurrency);
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            semaphore.acquire();
            String message = "hello %d".formatted(i);
            HttpRequest request = HttpRequest.newBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString(message))
                    .uri(URI.create("http://localhost:%d/echo".formatted(port)))
                    .header("Content-Type", "text/plain")
                    .build();
            CompletableFuture<HttpResponse<String>> cf = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            cf.whenComplete((res, ex) -> semaphore.release());
            futures.add(cf);
        }
        for (int i = 0; i < requestCount; i++) {
            String expected = "hello %d".formatted(i);
            HttpResponse<String> res = futures.get(i).get();
            Assertions.assertEquals(200, res.statusCode());
            Assertions.assertEquals("text/plain", res.headers().firstValue("Content-Type").get());
            Assertions.assertEquals(expected, res.body());
        }
    }

    static Stream<Arguments> argsProvider() {
        return Stream.of(
                Arguments.arguments(1, 10),
                Arguments.arguments(10, 100),
                Arguments.arguments(100, 1_000));
    }

    static class DisabledLogger implements Logger {
        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public void log(LogEntry... entries) {

        }

        @Override
        public void log(Exception e, LogEntry... entries) {

        }
    }

}
