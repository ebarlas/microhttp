package org.microhttp;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EventLoopRestartTest {

    @Test
    public void stoppingShouldReleasePort() throws IOException, InterruptedException {
        Options options = OptionsBuilder.newBuilder()
                .withPort(0)
                .build();
        TestLogger logger = new TestLogger();
        Handler handler = (request, callback) -> callback.accept(new Response(200, "OK", List.of(), "".getBytes(StandardCharsets.UTF_8)));
        EventLoop eventLoop = null;
        EventLoop secondEventLoop = null;
        try {

            eventLoop = new EventLoop(options, (Logger) logger, (org.microhttp.Handler) handler);
            eventLoop.start();
            int port = eventLoop.getPort();
            eventLoop.stop();
            eventLoop.join();

            options = OptionsBuilder.newBuilder()
                    .withPort(port)
                    .build();
            secondEventLoop = new EventLoop(options, (Logger) logger, (org.microhttp.Handler) handler);
            secondEventLoop.start();
        } finally {
            if (eventLoop != null) {
                eventLoop.stop();
                eventLoop.join();
            }

            if (secondEventLoop != null) {
                secondEventLoop.stop();
                secondEventLoop.join();
            }
        }
    }

    @Test
    public void stoppingShouldReleasePortAfterHandledResponse() throws IOException, InterruptedException {
        Options options = OptionsBuilder.newBuilder()
                .withPort(0)
                .build();
        TestLogger logger = new TestLogger();
        String responseBody = "test";
        Handler handler = (request, callback) -> callback.accept(new Response(
                200,
                "OK",
                List.of(new Header("Content-Type", "text/plain")),
                responseBody.getBytes(StandardCharsets.UTF_8)
        ));
        EventLoop eventLoop = null;
        EventLoop secondEventLoop = null;
        Collection<Closeable> resourcesToClose = new ArrayList<>();
        try {

            eventLoop = new EventLoop(options, (Logger) logger, (org.microhttp.Handler) handler);
            eventLoop.start();
            int port = eventLoop.getPort();

            Socket socket = new Socket("localhost", port);
            socket.setSoTimeout(5_000);
            resourcesToClose.add(socket);
            InputStream inputStream = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream();

            outputStream.write(EventLoopTest.HTTP10_REQUEST.getBytes());
            byte[] received = inputStream.readAllBytes();
            Assertions.assertEquals(
                    EventLoopTest.HTTP10_RESPONSE.formatted(responseBody.length(), responseBody),
                    new String(received, StandardCharsets.UTF_8)
            );

            eventLoop.stop();
            eventLoop.join();
            socket.close();

            options = OptionsBuilder.newBuilder()
                    .withPort(port)
                    .build();
            secondEventLoop = new EventLoop(options, (Logger) logger, (org.microhttp.Handler) handler);
            secondEventLoop.start();

            socket = new Socket("localhost", port);
            resourcesToClose.add(socket);
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();

            outputStream.write(EventLoopTest.HTTP10_REQUEST.getBytes());
            received = inputStream.readAllBytes();
            Assertions.assertEquals(
                    EventLoopTest.HTTP10_RESPONSE.formatted(responseBody.length(), responseBody),
                    new String(received, StandardCharsets.UTF_8)
            );

        } finally {
            if (eventLoop != null) {
                eventLoop.stop();
                eventLoop.join();
            }

            if (secondEventLoop != null) {
                secondEventLoop.stop();
                secondEventLoop.join();
            }

            for (Closeable closeable : resourcesToClose) {
                closeable.close();
            }

        }
    }
}
