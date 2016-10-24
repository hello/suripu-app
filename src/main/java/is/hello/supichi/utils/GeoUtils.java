package is.hello.supichi.utils;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.google.common.base.Optional;
import com.maxmind.geoip2.DatabaseReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Created by ksg on 10/17/16
 */
public class GeoUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeoUtils.class);

    public static Optional<DatabaseReader> geoIPDatabase() {
        final File database = new File("/tmp/GeoLite2-City.mmdb");

        if (!database.exists()) {
            final AmazonS3 s3 = new AmazonS3Client(new DefaultAWSCredentialsProviderChain());
            s3.getObject(new GetObjectRequest("hello-deploy", "runtime-dependencies/GeoLite2-City.mmdb"), database);
        }

        try {
            final DatabaseReader reader = new DatabaseReader.Builder(database).build();
            return Optional.of(reader);
        } catch (IOException io) {
            LOGGER.warn("warning=issues-fetching-geoip-db error_msg={}", io.getMessage());
            return Optional.absent();
        }
    }

}
