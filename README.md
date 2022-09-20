# Microhttp

Microhttp is a fast, scalable, event-driven, self-contained Java web server that is small enough for a programmer to understand 
and reason about. It does not rely on any classpath dependencies or native code.

It is capable of serving over 1,000,000 requests per second on a commodity EC2 host (c5.2xlarge).
[TechEmpower](https://www.techempower.com/benchmarks/) continuous benchmarking results consistently show Microhttp
achieves over 2,000,000 requests per second. 

Comprehensibility is the highest priority. This library is intended to be an alternative to commonly used 
frameworks with overwhelming complexity.

Microhttp discretizes all requests and responses. Streaming is not supported. 
This aligns well with transactional web services that exchange small payloads.

Microhttp supports aspects of HTTP 1.0 and HTTP 1.1, but it is _not_ fully compliant with the spec
([RFC 2616](https://datatracker.ietf.org/doc/html/rfc2616), [RFC 7230](https://datatracker.ietf.org/doc/html/rfc7230), etc.)
`100-Continue` ([RFC 2616 8.2.3](https://datatracker.ietf.org/doc/html/rfc2616#section-8.2.3)) is not implemented, for example.

TLS is not supported. Edge proxies and load balancers provide this capability. 
The last hop to Microhttp typically does not require TLS.

HTTP 2 is not supported for a similar reason. 
Edge proxies can support HTTP 2 while using HTTP 1.1 on the last hop to Microhttp.

Microhttp is 100% compatible with [Project Loom](https://openjdk.org/projects/loom/) [virtual threads](https://openjdk.org/jeps/425).
Simply handle each request in a separate virtual thread, invoking the callback function upon completion.

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

Intended Use:
* Teaching or learning scalable concurrency, NIO, HTTP, networking
* Mock or stub servers for testing
* Internal web servers not exposed to the internet
* Web server behind an internet-facing reverse proxy (Nginx, HAProxy, AWS ELB, etc)

# Dependency

Microhttp is available in the Maven Central repository with group `org.microhttp`
and artifact `microhttp`.

```xml
<dependency>
    <groupId>org.microhttp</groupId>
    <artifactId>microhttp</artifactId>
    <version>0.8</version>
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

These benchmark were performed on July 12, 2022 with commit `78f54e84e86cdd038c87baaf45b7973a8f088cf7`.

The experiments detailed below were conducted on a pair of EC2 instances in AWS,
one running the server and another running the client.

* Region: `us-west-2`
* Instance type: `c5.2xlarge` compute optimized instance 8 vCPU and 16 GB of memory
* OS: Amazon Linux 2 with Linux Kernel 5.10, AMI `ami-00f7e5c52c0f43726`
* OpenJDK 18.0.1.1 from https://jdk.java.net/18/

The [wrk](https://github.com/wg/wrk) HTTP benchmarking tool was used to generate load on the client
EC2 instance.

## Throughput

The goal of throughput benchmarks is to gauge the maximum request-per-second rate
that can be supported by Microhttp. These experiments are intended to surface the costs
and limitations of Microhttp alone. They are not intended to provide a real world estimate
of throughput in an integrated system with many components and dependencies.

### Server

[ThroughputServer.java](https://gist.github.com/ebarlas/c0c33e2bed17009dfe4bcec5d3a06e05) was used for throughput tests.

It simply returns "hello world" in a tiny, plain-text response to every request. Requests are handled in the context of
the event loop thread, directly within the `Handler.handle` method.

```
./jdk-18.0.1.1/bin/java -cp microhttp-0.8-SNAPSHOT.jar org.microhttp.ThroughputServer
```

### Benchmark

With HTTP pipelining, a request rate of over 1,000,000 requests per second was consistently reproducible.

In the 1-minute run below, a rate of `1,098,810` requests per second was achieved.

* 100 concurrent connections
* 1 wrk worker threads
* 10 second timeout
* 16 pipelined requests

No custom kernel parameters were set beyond the AMI defaults for this test.

No errors occurred and the 99th percentile response time was quite reasonable, 
given that client and server were both CPU-bound.

```
$ date
Tue Jul 12 17:11:05 UTC 2022

$ ./wrk -H "Host: 10.39.196.71:8080" -H "Accept: text/plain" -H "Connection: keep-alive" --latency -d 60s -c 100 --timeout 10 -t 1 http://10.39.196.71:8080/ -s pipeline.lua -- 16
Running 1m test @ http://10.39.196.71:8080/
  1 threads and 100 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    18.44ms   13.95ms  52.12ms   53.25%
    Req/Sec     1.10M    22.87k    1.14M    87.83%
  Latency Distribution
     50%   18.37ms
     75%   31.47ms
     90%   39.33ms
     99%    0.00us
  65929433 requests in 1.00m, 4.73GB read
Requests/sec: 1098810.79
Transfer/sec:     80.69MB
```

***

Without HTTP pipelining, a request rate of over 450,000 requests per second was consistently reproducible.

In the 1-minute run below, a rate of `454,796` requests per second was achieved.

* 100 concurrent connections
* 8 wrk worker threads
* 10 second timeout

No errors occurred and the 99th percentile response time was exceptional.

```
$ date
Tue Jul 12 17:16:49 UTC 2022
 
$ ./wrk -H "Host: 10.39.196.71:8080" -H "Accept: text/plain" -H "Connection: keep-alive" --latency -d 60s -c 100 --timeout 10 -t 8 http://10.39.196.71:8080/
Running 1m test @ http://10.39.196.71:8080/
  8 threads and 100 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   218.65us    1.64ms 212.93ms   99.97%
    Req/Sec    57.15k     4.68k   69.47k    85.19%
  Latency Distribution
     50%  188.00us
     75%  229.00us
     90%  277.00us
     99%  372.00us
  27332950 requests in 1.00m, 1.96GB read
Requests/sec: 454796.26
Transfer/sec:     33.40MB
```

## Concurrency

The goal of concurrency benchmarks is to gauge the number of concurrent connections and clients
that can be supported by Microhttp.

In order to facilitate the rapid creation of 50,000 connections, the following `sysctl` kernel parameter changes
were committed on both hosts prior to the start of the experiment:

```
sysctl net.ipv4.ip_local_port_range="2000 64000"
sysctl net.ipv4.tcp_fin_timeout=30
sysctl net.core.somaxconn=8192
sysctl net.core.netdev_max_backlog=8000
sysctl net.ipv4.tcp_max_syn_backlog=8192
```

### Server

[ConcurrencyServer.java](https://gist.github.com/ebarlas/b432f589246eae00e8a7cf7e680c367a) was used for concurrency tests.

"hello world" responses are handled in a separate background thread after an injected one-second delay.
The one-second delay dramatically reduces the resource footprint since requests and responses
aren't speeding over each connection continuously. This leaves room to scale up connections, which is the metric of interest.

```
./jdk-18.0.1.1/bin/java -cp microhttp-0.8-SNAPSHOT.jar org.microhttp.ConcurrencyServer 8192
```

### Benchmark

A concurrency level of 50,000 connections without error was consistently reproducible.

* 50,000 concurrent connections
* 16 wrk worker threads
* 10 second timeout

No errors occurred.

The quality of service is stellar. The 99% percentile response time it 1.01 seconds, just 0.01 above
the target latency introduced on the server.

```
$ date
Tue Jul 12 17:26:53 UTC 2022

$ ./wrk -H "Host: 10.39.196.71:8080" -H "Accept: text/plain" -H "Connection: keep-alive" --latency -d 60s -c 50000 --timeout 10 -t 16 http://10.39.196.71:8080/
Running 1m test @ http://10.39.196.71:8080/
  16 threads and 50000 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.00s     2.74ms   1.21s    95.44%
    Req/Sec     8.52k    11.02k   31.56k    73.64%
  Latency Distribution
     50%    1.00s 
     75%    1.00s 
     90%    1.00s 
     99%    1.01s 
  2456381 requests in 1.00m, 180.38MB read
Requests/sec:  40875.87
Transfer/sec:      3.00MB
```