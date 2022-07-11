# Microhttp

Microhttp is a fast, scalable, event-driven, self-contained Java web server that is small enough for a programmer to understand 
and reason about.

Comprehensibility is the highest priority. This library is intended to be an alternative to commonly used 
frameworks with overwhelming complexity. 
Implementation decisions aim to strike a balance between simplicity and efficiency.

Microhttp discretizes all requests and responses. Streaming is not supported. 
This aligns well with transactional web services that exchange small payloads. 
Limiting request body size has the added benefit of overflow protection. 
This is frequently overlooked in web services that consume request bodies in a stream-oriented fashion.

TLS is not supported. Edge proxies and load balancers provide this capability. 
The last hop to Microhttp typically does not require TLS.

HTTP 2 is not supported for a similar reason. 
Edge proxies can support HTTP 2 while using HTTP 1.1 on the last hop to Microhttp.

Principles:
* No dependencies
* Small, targeted codebase (~500 LOC)
* Highly concurrent
* Single-threaded event loops
* Event-driven non-blocking NIO
* No TLS support
* No streaming support
* Traceability via log events

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

# Dependency

Microhttp is available in the Maven Central repository with group `org.microhttp`
and artifact `microhttp`.

```xml
<dependency>
    <groupId>org.microhttp</groupId>
    <artifactId>microhttp</artifactId>
    <version>0.7</version>
</dependency>
```

# Getting Started

The snippet below represents a minimal starting point.
Default options and debug logging.

The application consists of an event loop running in a background thread.

Responses are handled immediately in the `Handler.handle` method.

```java
Response response = new Response(
        200,
        "OK",
        List.of(new Header("Content-Type", "text/plain")),
        "hello world\n".getBytes());
Handler handler = (req, callback) -> callback.accept(response);
EventLoop eventLoop = new EventLoop(handler);
eventLoop.start();
eventLoop.join();
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
        .withRequestTimeout(Duration.ofSeconds(60))
        .withResolution(Duration.ofMillis(100))
        .withBufferSize(1_024 * 64)
        .withMaxRequestSize(1_024 * 1_024)
        .withAcceptLength(0)
        .withConcurrency(4);
Logger logger = new DebugLogger();
Handler handler = (req, callback) -> callback.accept(response);
EventLoop eventLoop = new EventLoop(options, logger, handler);
eventLoop.start();
eventLoop.join();
```

***

The example below demonstrates asynchronous request handling.

Responses are handled in a separate background thread after
an artificial one-second delay.

```java
Response response = new Response(
        200,
        "OK",
        List.of(new Header("Content-Type", "text/plain")),
        "hello world\n".getBytes());
ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
Handler handler = (req, callback) -> executorService.schedule(() -> callback.accept(response), 1, TimeUnit.SECONDS);
EventLoop eventLoop = new EventLoop(handler);
eventLoop.start();
eventLoop.join();
```

# Benchmarks

The [benchmarks](benchmarks.md) doc outlines the concurrency and throughput
scale achieved using a representative AWS cloud environment with separate
EC2 instances for client and server applications.