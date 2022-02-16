package microhttp;

import java.time.Duration;

public class Options {

    private String host = "localhost";
    private int port = 8080;
    private Duration resolution = Duration.ofMillis(100);
    private Duration socketTimeout = Duration.ofSeconds(60);
    private int readBufferSize = 1_024 * 64;
    private int writeBufferSize = 1_024 * 64;
    private int acceptLength = 0;
    private int maxRequestSize = 1_024 * 1_024;

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public Duration resolution() {
        return resolution;
    }

    public Duration socketTimeout() {
        return socketTimeout;
    }

    public int readBufferSize() {
        return readBufferSize;
    }

    public int writeBufferSize() {
        return writeBufferSize;
    }

    public int acceptLength() {
        return acceptLength;
    }

    public int maxRequestSize() {
        return maxRequestSize;
    }

    public Options withHost(String host) {
        this.host = host;
        return this;
    }

    public Options withPort(int port) {
        this.port = port;
        return this;
    }

    public Options withResolution(Duration resolution) {
        this.resolution = resolution;
        return this;
    }

    public Options withSocketTimeout(Duration socketTimeout) {
        this.socketTimeout = socketTimeout;
        return this;
    }

    public Options withReadBufferSize(int readBufferSize) {
        this.readBufferSize = readBufferSize;
        return this;
    }

    public Options withWriteBufferSize(int writeBufferSize) {
        this.writeBufferSize = writeBufferSize;
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
}
