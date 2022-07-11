package org.microhttp;

import java.time.Duration;

public class Options {

    private String host = "localhost";
    private int port = 8080;
    private boolean reuseAddr = false;
    private boolean reusePort = false;
    private Duration resolution = Duration.ofMillis(100);
    private Duration requestTimeout = Duration.ofSeconds(60);
    private int bufferSize = 1_024 * 64;
    private int acceptLength = 0;
    private int maxRequestSize = 1_024 * 1_024;
    private int concurrency = Runtime.getRuntime().availableProcessors();

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public boolean reuseAddr() {
        return reuseAddr;
    }

    public boolean reusePort() {
        return reusePort;
    }

    public Duration resolution() {
        return resolution;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    public int readBufferSize() {
        return bufferSize;
    }

    public int acceptLength() {
        return acceptLength;
    }

    public int maxRequestSize() {
        return maxRequestSize;
    }

    public int concurrency() {
        return concurrency;
    }

    public Options withHost(String host) {
        this.host = host;
        return this;
    }

    public Options withPort(int port) {
        this.port = port;
        return this;
    }

    public Options withReuseAddr(boolean reuseAddr) {
        this.reuseAddr = reuseAddr;
        return this;
    }

    public Options withReusePort(boolean reusePort) {
        this.reusePort = reusePort;
        return this;
    }

    public Options withResolution(Duration resolution) {
        this.resolution = resolution;
        return this;
    }

    public Options withRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
        return this;
    }

    public Options withReadBufferSize(int readBufferSize) {
        this.bufferSize = readBufferSize;
        return this;
    }

    public Options withAcceptLength(int acceptLength) {
        this.acceptLength = acceptLength;
        return this;
    }

    public Options withMaxRequestSize(int maxRequestSize) {
        this.maxRequestSize = maxRequestSize;
        return this;
    }

    public Options withConcurrency(int concurrency) {
        this.concurrency = concurrency;
        return this;
    }
}
