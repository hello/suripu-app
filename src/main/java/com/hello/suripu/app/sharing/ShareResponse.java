package com.hello.suripu.app.sharing;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ShareResponse {

    private final static String HOST = "https://share.hello.is/";
    private final String url;

    private ShareResponse(final String url) {
        this.url = url;
    }

    public static ShareResponse create(String url) {
        return new ShareResponse(url);
    }

    public static ShareResponse createInsight(String url) {
        return new ShareResponse(String.format("insight/%s",url));
    }

    @JsonProperty("url")
    public String url() {
        return HOST + url;
    }
}
