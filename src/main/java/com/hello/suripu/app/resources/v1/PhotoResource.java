package com.hello.suripu.app.resources.v1;

import com.amazonaws.services.s3.AmazonS3;
import com.google.common.base.Optional;
import com.hello.suripu.app.configuration.PhotoUploadConfiguration;
import com.hello.suripu.core.models.MultiDensityImage;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.profile.ImmutableProfilePhoto;
import com.hello.suripu.core.profile.ProfilePhotoStore;
import com.hello.suripu.coredw8.oauth.AccessToken;
import com.hello.suripu.coredw8.oauth.Auth;
import com.hello.suripu.coredw8.oauth.ScopesAllowed;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Path("/v1/photo")
public class PhotoResource {

    private final Logger LOGGER = LoggerFactory.getLogger(PhotoResource.class);

    private final AmazonS3 amazonS3;
    private final PhotoUploadConfiguration config;
    private final ProfilePhotoStore profilePhotoStore;

    public PhotoResource(final AmazonS3 amazonS3, final PhotoUploadConfiguration photoUploadConfiguration, final ProfilePhotoStore profilePhotoStore){
        this.amazonS3 = amazonS3;
        this.config = photoUploadConfiguration;
        this.profilePhotoStore = profilePhotoStore;
    }


    @ScopesAllowed(OAuthScope.USER_EXTENDED)
    @POST
    @Path("/profile")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public MultiDensityImage upload(@Auth AccessToken accessToken,
                                    @Context final HttpServletRequest request,
                                    @FormDataParam("file") InputStream uploadedInputStream,
                                    @FormDataParam("file") FormDataContentDisposition fileDetail) {


        int contentLength = request.getContentLength();

        if (contentLength == -1 || contentLength > config.maxUploadSizeInBytes()) {
            LOGGER.error("error=invalid-content-length value={}", contentLength);
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }

        final UUID uuid = UUID.randomUUID();
        final String cleanUUID = uuid.toString().replace("-","");

        final String key = FilenameUtils.normalize(
                String.format("%s/%s.jpeg",
                        config.profilePrefix(),
                        cleanUUID
                ));

        try{
            // needed so we don't buffer uploads in memory
            final File tempFile = new File("/tmp/" + cleanUUID);
            FileUtils.copyInputStreamToFile(uploadedInputStream, tempFile);
            amazonS3.putObject(config.bucketName(), key, tempFile);
            final boolean deleted = FileUtils.deleteQuietly(tempFile);
            if(!deleted) {
                LOGGER.warn("action=delete-file filename={} result=failed", cleanUUID);
            }
        } catch (IOException e) {
            LOGGER.error("error=profile-photo-upload-failed msg={}", e.getMessage());
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }

        final String url = String.format("https://s3.amazonaws.com/%s/%s", config.bucketName(), key);

        final MultiDensityImage image = new MultiDensityImage(
                Optional.of(url),
                Optional.of(url),
                Optional.of(url)
        );

        final ImmutableProfilePhoto immutableProfilePhoto = ImmutableProfilePhoto.builder()
                .accountId(accessToken.accountId)
                .photo(image)
                .createdAt(DateTime.now(DateTimeZone.UTC))
                .build();
        profilePhotoStore.put(immutableProfilePhoto);
        return image;
    }


    @ScopesAllowed(OAuthScope.USER_EXTENDED)
    @DELETE
    @Path("/profile")
    public Response delete(@Auth AccessToken accessToken) {
        this.profilePhotoStore.delete(accessToken.accountId);
        throw new WebApplicationException(204);
    }

}
