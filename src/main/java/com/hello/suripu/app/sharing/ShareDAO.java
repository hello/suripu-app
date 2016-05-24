package com.hello.suripu.app.sharing;

public interface ShareDAO {
    String put(Share share);
    void delete(String shareId);
}
