package com.parse.livequery;

import java.util.Locale;

public abstract class LiveQueryException extends Exception {

    private LiveQueryException() {
        super();
    }

    private LiveQueryException(String detailMessage) {
        super(detailMessage);
    }

    private LiveQueryException(String detailMessage, Throwable cause) {
        super(detailMessage, cause);
    }

    private LiveQueryException(Throwable cause) {
        super(cause);
    }

    /**
     * An error that is reported when any other unknown {@link RuntimeException} occurs unexpectedly.
     */
    public static class UnknownException extends LiveQueryException {
        /* package */ UnknownException(String detailMessage, RuntimeException cause) {
            super(detailMessage, cause);
        }
    }

    /**
     * An error that is reported when the server returns a response that cannot be parsed.
     */
    public static class InvalidResponseException extends LiveQueryException {
        /* package */ InvalidResponseException(String response) {
            super(response);
        }
    }

    /**
     * An error that is reported when the server does not accept a query we've sent to it.
     */
    public static class InvalidQueryException extends LiveQueryException {

    }

    /**
     * An error that is reported when the server returns valid JSON, but it doesn't match the format we expect.
     */
    public static class InvalidJSONException extends LiveQueryException {
        // JSON used for matching.
        private final String json;
        /// Key that was expected to match.
        private final String expectedKey;

        /* package */ InvalidJSONException(String json, String expectedKey) {
            super(String.format(Locale.US, "Invalid JSON; expectedKey: %s, json: %s", expectedKey, json));
            this.json = json;
            this.expectedKey = expectedKey;
        }

        public String getJson() {
            return json;
        }

        public String getExpectedKey() {
            return expectedKey;
        }
    }

    /**
     * An error that is reported when the live query server encounters an internal error.
     */
    public static class ServerReportedException extends LiveQueryException {

        private final int code;
        private final String error;
        private final boolean reconnect;

        public ServerReportedException(int code, String error, boolean reconnect) {
            super(String.format(Locale.US, "Server reported error; code: %d, error: %s, reconnect: %b", code, error, reconnect));
            this.code = code;
            this.error = error;
            this.reconnect = reconnect;
        }

        public int getCode() {
            return code;
        }

        public String getError() {
            return error;
        }

        public boolean isReconnect() {
            return reconnect;
        }
    }

}
