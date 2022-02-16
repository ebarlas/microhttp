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
* Single threaded
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
    <version>0.2</version>
</dependency>
```

# Getting Started

The snippet below represents a minimal starting point.
Default options and debug logging.

The application consists of an event loop running in the main thread.
There are no additional application threads.

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
Handler handler = (req, callback) -> callback.accept(response);
EventLoop eventLoop = new EventLoop(options, logger, handler);
eventLoop.start();
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
```

***

This example demonstrates the use of a separate thread for the event loop.


```java
Response response = new Response(
        200,
        "OK",
        List.of(new Header("Content-Type", "text/plain")),
        "hello world\n".getBytes());
ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
Handler handler = (req, callback) -> executorService.schedule(() -> callback.accept(response), 1, TimeUnit.SECONDS);
EventLoop eventLoop = new EventLoop(handler);
Thread thread = new Thread(eventLoop::start);
thread.start();
// ...
eventLoop.stop();
thread.join();
```

# Benchmarks

The experiments detailed below were conducted on a pair of EC2 instances in AWS, 
one running the server and another running the client.

* Region: `us-west-2`
* Instance type: `c5.2xlarge` compute optimized instance 8 vCPU and 16 GB of memory
* OS: Amazon Linux 2 with Linux Kernel 5.10, AMI `ami-00f7e5c52c0f43726`
* OpenJDK 17.0.2 from https://jdk.java.net/17/

In order to facilitate the rapid creation of 50,000 connections, the following `sysctl` kernel parameter changes
were committed on both hosts prior to the start of the experiment:

```
sysctl net.ipv4.ip_local_port_range="2000 64000"
sysctl net.ipv4.tcp_fin_timeout=30
sysctl net.core.somaxconn=8192
sysctl net.core.netdev_max_backlog=8000
sysctl net.ipv4.tcp_max_syn_backlog=8192
```

## Throughput

The goal of throughput benchmarks is to gauge the maximum request-per-second rate 
that can be supported by Microhttp. These experiments are intended to surface the costs
and limitations of Microhttp alone. They are not intended to provide a real world estimate
of throughput in an integrated system with many components and dependencies.

### Server

[ThroughputServer.java](https://gist.github.com/ebarlas/d932bda5901c2907596cc53bd89c781a) was used for throughput tests.

It simply returns "hello world" in a tiny, plain-text response to every request. Requests are handled in the context of
the main application thread, directly within the `Handler.handle` method.  

### Apache Bench

The first throughput test was conducted with [Apache Bench](https://httpd.apache.org/docs/2.4/programs/ab.html).

A throughput of 100,000 requests per second was easily reproducible.

```text
[ec2-user@ip-10-39-196-99 ~]$ ab -k -c 100 -n 1000000 http://10.39.196.164:8080/
This is ApacheBench, Version 2.3 <$Revision: 1879490 $>
Copyright 1996 Adam Twiss, Zeus Technology Ltd, http://www.zeustech.net/
Licensed to The Apache Software Foundation, http://www.apache.org/

Benchmarking 10.39.196.164 (be patient)
Completed 100000 requests
Completed 200000 requests
Completed 300000 requests
Completed 400000 requests
Completed 500000 requests
Completed 600000 requests
Completed 700000 requests
Completed 800000 requests
Completed 900000 requests
Completed 1000000 requests
Finished 1000000 requests


Server Software:        
Server Hostname:        10.39.196.164
Server Port:            8080

Document Path:          /
Document Length:        12 bytes

Concurrency Level:      100
Time taken for tests:   9.964 seconds
Complete requests:      1000000
Failed requests:        0
Keep-Alive requests:    1000000
Total transferred:      101000000 bytes
HTML transferred:       12000000 bytes
Requests per second:    100364.03 [#/sec] (mean)
Time per request:       0.996 [ms] (mean)
Time per request:       0.010 [ms] (mean, across all concurrent requests)
Transfer rate:          9899.19 [Kbytes/sec] received

Connection Times (ms)
              min  mean[+/-sd] median   max
Connect:        0    0   0.0      0       1
Processing:     1    1   0.0      1       3
Waiting:        0    1   0.0      1       3
Total:          1    1   0.0      1       3

Percentage of the requests served within a certain time (ms)
  50%      1
  66%      1
  75%      1
  80%      1
  90%      1
  95%      1
  98%      1
  99%      1
 100%      3 (longest request)
```

### NIO Client

A second throughput client, [Client.java](https://gist.github.com/ebarlas/41d31a0ba546b879681c7e7007d75107), 
was implemented with Java NIO and tailored specifically for Microhttp throughput testing.

Again, 100,000+ requests per second was readily available.

```
[ec2-user@ip-10-39-196-99 ~]$ ./jdk-17.0.2/bin/java -cp microhttp-0.1-SNAPSHOT.jar test.Client 10.39.196.164 8080 100 30000
Args[host=10.39.196.164, port=8080, numConnections=100, duration=30000]
barrier opened!
duration: 30001 ms, messages: 3250567, throughput: 108348.621713 msg/sec
```

## Concurrency

The goal of concurrency benchmarks is to gauge the number of concurrent connections and clients
that can be supported by Microhttp.

### Server

[ConcurrencyServer.java](https://gist.github.com/ebarlas/42aa8ce6ced6573b46a3e4e36d312535) was used for concurrency tests.

"hello world" responses are handled in a separate background thread after an injected one-second delay. 
The one-second delay dramatically reduces the resource footprint since requests and responses
aren't speeding over each connection continuously. This leaves room to scale up connections, which is the metric of interest.

### Apache Bench

[Apache Bench](https://httpd.apache.org/docs/2.4/programs/ab.html) only supports a maximum of 20,000 concurrency connections.

Microhttp holds up well. 

```
[ec2-user@ip-10-39-196-99 ~]$ ab -k -c 20000 -n 100000 http://10.39.196.164:8080/
This is ApacheBench, Version 2.3 <$Revision: 1879490 $>
Copyright 1996 Adam Twiss, Zeus Technology Ltd, http://www.zeustech.net/
Licensed to The Apache Software Foundation, http://www.apache.org/

Benchmarking 10.39.196.164 (be patient)
Completed 10000 requests
Completed 20000 requests
Completed 30000 requests
Completed 40000 requests
Completed 50000 requests
Completed 60000 requests
Completed 70000 requests
Completed 80000 requests
Completed 90000 requests
Completed 100000 requests
Finished 100000 requests


Server Software:        
Server Hostname:        10.39.196.164
Server Port:            8080

Document Path:          /
Document Length:        12 bytes

Concurrency Level:      20000
Time taken for tests:   6.825 seconds
Complete requests:      100000
Failed requests:        0
Keep-Alive requests:    100000
Total transferred:      10100000 bytes
HTML transferred:       1200000 bytes
Requests per second:    14652.72 [#/sec] (mean)
Time per request:       1364.934 [ms] (mean)
Time per request:       0.068 [ms] (mean, across all concurrent requests)
Transfer rate:          1445.24 [Kbytes/sec] received

Connection Times (ms)
              min  mean[+/-sd] median   max
Connect:        0   42  84.6      0     266
Processing:  1000 1048  54.2   1013    1256
Waiting:     1000 1048  54.2   1013    1174
Total:       1000 1090 117.3   1022    1349

Percentage of the requests served within a certain time (ms)
  50%   1022
  66%   1091
  75%   1137
  80%   1256
  90%   1313
  95%   1322
  98%   1328
  99%   1332
 100%   1349 (longest request)
```

### NIO Client

A concurrency level of 50,000 connections was achievable using [Client.java](https://gist.github.com/ebarlas/41d31a0ba546b879681c7e7007d75107).

```
[ec2-user@ip-10-39-196-99 ~]$ ./jdk-17.0.2/bin/java -cp microhttp-0.1-SNAPSHOT.jar test.Client 10.39.196.164 8080 50000 60000
Args[host=10.39.196.164, port=8080, numConnections=50000, duration=60000]
barrier opened!
duration: 61001 ms, messages: 2968864, throughput: 48669.103785 msg/sec
```