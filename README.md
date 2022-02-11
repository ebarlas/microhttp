# Microhttp

Microhttp is a fast, scalable, self-contained Java web server that is small enough for a programmer to understand 
and reason about.

Comprehensibility is the highest priority. This library is intended to be an alternative to commonly used 
frameworks with overwhelming complexity. 
Implementation decisions aim to strike a balance between simplicity and efficiency.

Microhttp discretizes all requests and responses. Streaming is not supported. 
This aligns well with transactional web services that exchange small payloads. 
Limiting request body size has the added benefit of overflow protection. 
This is frequently overlooked in web services that consume request bodies in a stream-oriented fashion.

TLS is not supported. Edge proxies and load balancers provide this capability. 
The last hop to microhttp typically does not require TLS.

HTTP 2 is not supported for a similar reason. 
Edge proxies can support HTTP 2 while using HTTP 1.1 on the last hop to microhttp.

Principles:
* No dependencies
* Small, targeted codebase
* Highly concurrent
* Single threaded
* Built atop non-blocking NIO
* No TLS support
* No streaming support
* Network trace events

Includes:
* HTTP 1.0 and 1.1
* Chunked transfer encoding
* Persistent connections
* Pipelining

Excludes:
* HTTP 2
* Range requests
* Caching
* Compression

# Getting Started

The snippet below represents a minimal starting point.
Default options and debug logging.
The entire application consists of a single event loop thread.
Responses are handled immediately in the event loop thread.

```java
Response response = new Response(
        200,
        "OK",
        List.of(new Header("Content-Type", "text/plain")),
        "hello world\n".getBytes());
EventLoop eventLoop = new EventLoop((req, callback) -> callback.accept(response));
eventLoop.start();
```

***

The following example demonstrates the full range of configuration options.

```java
Response response = new Response(
        200,
        "OK",
        List.of(new Header("Content-Type", "text/plain")),
        "hello world\n".getBytes());
Options options = new Options()
        .withHost("localhost")
        .withPort(8080)
        .withSocketTimeout(Duration.ofSeconds(60))
        .withResolution(Duration.ofMillis(100))
        .withReadBufferSize(1_024 * 64)
        .withMaxRequestSize(1_024 * 1_024)
        .withAcceptLength(0);
Logger logger = new DebugLogger();
EventLoop eventLoop = new EventLoop(options, logger, (req, callback) -> callback.accept(response));
eventLoop.start();
```

***

The example below demonstrates asynchronous request handling.
Responses are handled in a separate background thread after
a artificial one-second delay.

```java
Response response = new Response(
        200,
        "OK",
        List.of(new Header("Content-Type", "text/plain")),
        "hello world\n".getBytes());
ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
EventLoop eventLoop = new EventLoop((req, callback) -> 
        executorService.schedule(() -> callback.accept(response), 1, TimeUnit.SECONDS));
eventLoop.start();
```