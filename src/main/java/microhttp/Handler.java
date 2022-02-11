package microhttp;

import java.util.function.Consumer;

/**
 * HTTP request handler.
 */
public interface Handler {

    /**
     * Handle HTTP request.
     * The provided callback object has a reference to internal connection state.
     * The callee MUST invoke the callback once and only once.
     */
    void handle(Request request, Consumer<Response> callback);

}
