package com.hello.suripu.app.sharing;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ShareResponse {

    private final String url;

    private ShareResponse(final String url) {
        this.url = url;
    }

    public static ShareResponse create(String url) {
        return new ShareResponse(url);
    }

    @JsonProperty("url")
    public String url() {
        return url;
    }
}
