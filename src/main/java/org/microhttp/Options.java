package org.microhttp;

import java.time.Duration;

public class Options {

    private final String host;
    private final int port;
    private final boolean reuseAddr;
    private final boolean reusePort;
    private final Duration resolution;
    private final Duration requestTimeout;
    private final int readBufferSize;
    private final int acceptLength;
    private final int maxRequestSize;
    private final int concurrency;

    private Options(OptionsBuilder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.reuseAddr = builder.reuseAddr;
        this.reusePort = builder.reusePort;
        this.resolution = builder.resolution;
        this.requestTimeout = builder.requestTimeout;
        this.readBufferSize = builder.readBufferSize;
        this.acceptLength = builder.acceptLength;
        this.maxRequestSize = builder.maxRequestSize;
        this.concurrency = builder.concurrency;
    }

    public static class OptionsBuilder {

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
            this. acceptLength = 0;
            this.maxRequestSize = 1_024 * 1_024;
            this.concurrency = Runtime.getRuntime().availableProcessors();
        }

        public static OptionsBuilder newBuilder() {
            return new OptionsBuilder();
        }

        public static Options getDefaultInstance() {
            return new Options(new OptionsBuilder());
        }

        public Options build() {
            return new Options(this);
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
        return readBufferSize;
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
}
