package org.microhttp;

import java.time.Duration;

public class OptionsBuilder {
    private String host;
    private int port;
    private boolean reuseAddr;
    private boolean reusePort;
    private Duration resolution;
    private Duration requestTimeout;
    private int readBufferSize;
    private int acceptLength;
    private int maxRequestSize;
    private int concurrency;

    private OptionsBuilder() {
        this.host = "localhost";
        this.port = 8080;
        this.reuseAddr = false;
        this.reusePort = false;
        this.resolution = Duration.ofMillis(100);
        this.requestTimeout = Duration.ofSeconds(60);
        this.readBufferSize = 1_024 * 64;
        this.acceptLength = 0;
        this.maxRequestSize = 1_024 * 1_024;
        this.concurrency = Runtime.getRuntime().availableProcessors();
    }

    public static OptionsBuilder newBuilder() {
        return new OptionsBuilder();
    }

    public Options build() {
        return new Options(this.host,
            this.port,
            this.reuseAddr,
            this.reusePort,
            this.resolution,
            this.requestTimeout,
            this.readBufferSize,
            this.acceptLength,
            this.maxRequestSize,
            this.concurrency);
    }

    public OptionsBuilder withHost(String host) {
        this.host = host;
        return this;
    }

    public OptionsBuilder withPort(int port) {
        this.port = port;
        return this;
    }

    public OptionsBuilder withReuseAddr(boolean reuseAddr) {
        this.reuseAddr = reuseAddr;
        return this;
    }

    public OptionsBuilder withReusePort(boolean reusePort) {
        this.reusePort = reusePort;
        return this;
    }

    public OptionsBuilder withResolution(Duration resolution) {
        this.resolution = resolution;
        return this;
    }

    public OptionsBuilder withRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
        return this;
    }

    public OptionsBuilder withReadBufferSize(int readBufferSize) {
        this.readBufferSize = readBufferSize;
        return this;
    }

    public OptionsBuilder withAcceptLength(int acceptLength) {
        this.acceptLength = acceptLength;
        return this;
    }

    public OptionsBuilder withMaxRequestSize(int maxRequestSize) {
        this.maxRequestSize = maxRequestSize;
        return this;
    }

    public OptionsBuilder withConcurrency(int concurrency) {
        this.concurrency = concurrency;
        return this;
    }
}
