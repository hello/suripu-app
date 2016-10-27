package is.hello.supichi.response;

import is.hello.supichi.api.Response;
import is.hello.supichi.api.Speech;
import is.hello.supichi.models.HandlerResult;
import net.spy.memcached.MemcachedClient;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachedResponseBuilder implements SupichiResponseBuilder {

    private final static Logger LOGGER = LoggerFactory.getLogger(CachedResponseBuilder.class);

    private final String voiceName;
    private final SupichiResponseBuilder responseBuilder;
    private final MemcachedClient mc;
    private final String cachePrefix;

    public CachedResponseBuilder(final String voiceName, final SupichiResponseBuilder wrapped, final MemcachedClient mc, final String cachePrefix) {
        this.voiceName = voiceName;
        this.responseBuilder = wrapped;
        this.mc = mc;
        this.cachePrefix = cachePrefix;
    }

    @Override
    public byte[] response(Response.SpeechResponse.Result result, HandlerResult handlerResult, Speech.SpeechRequest request) {

        // This assumes that responseText() always returns a non empty response
        final String text = handlerResult.responseText();

        final String md5Text = DigestUtils.md5Hex(text);
        // Allison-MP3-EQ-MD5(TEXT)
        final String cacheKey = String.format("%s:%s-%s-%s-%s",
                cachePrefix,
                voiceName,
                request.getResponse().name(),
                request.getEq().name(),
                md5Text
        );

        try {
            byte[] audio = (byte[]) mc.get(cacheKey);
            if(audio != null) {
                LOGGER.debug("action=get-cached-response key={}", cacheKey);
                return audio;
            }
        } catch (Exception e) {
            // If there was an error attempting to read from cache
            // bypass caching altogether
            LOGGER.error("error=memcache-get key={} message={}", cacheKey, e.getMessage());
            return responseBuilder.response(result, handlerResult, request);
        }

        final byte[] audioBytes = responseBuilder.response(result, handlerResult, request);
        try {
            // never expires
            mc.set(cacheKey, 0, audioBytes);
        } catch (Exception e) {
            LOGGER.error("error=memcache-set key={} message={}", cacheKey, e.getMessage());
        }
        return audioBytes;
    }
}
