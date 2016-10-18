package is.hello.speech.models.responsebuilder;

/**
 * Created by ksg on 8/8/16
 */
public class BuilderResponse {
    public final String s3Bucket;
    public final String s3Filename;
    public final String responseText;

    public BuilderResponse(final String s3Bucket, final String s3Filename, final String responseText) {
        this.s3Bucket = s3Bucket;
        this.s3Filename = s3Filename;
        this.responseText = responseText;
    }

    public static BuilderResponse empty() {
        return new BuilderResponse("", "", "");
    }
}
