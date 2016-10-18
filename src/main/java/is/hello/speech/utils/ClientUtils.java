package is.hello.speech.utils;

import com.google.auth.oauth2.GoogleCredentials;
import io.grpc.ManagedChannel;
import io.grpc.auth.ClientAuthInterceptor;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Created by ksg on 8/1/16
 */
public class ClientUtils {
    private static final List<String> OAUTH2_SCOPES =
            Arrays.asList("https://www.googleapis.com/auth/cloud-platform");


    public static ManagedChannel createChannel(String host, int port) throws IOException {
        GoogleCredentials creds = GoogleCredentials.getApplicationDefault();
        creds = creds.createScoped(OAUTH2_SCOPES);
        ManagedChannel channel =
                NettyChannelBuilder.forAddress(host, port)
                        .negotiationType(NegotiationType.TLS)
                        .intercept(new ClientAuthInterceptor(creds, Executors.newSingleThreadExecutor()))
                        .build();

        return channel;
    }

}
