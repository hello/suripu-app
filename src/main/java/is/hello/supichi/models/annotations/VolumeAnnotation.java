package is.hello.supichi.models.annotations;

/**
 * Created by ksg on 9/20/16
 */
public class VolumeAnnotation implements AnnotationInterface {
    private final String matchingText;
    private final Integer volumePercent;

    public VolumeAnnotation(final String matchingText, final Integer volumePercent) {
        this.matchingText = matchingText;
        this.volumePercent = volumePercent;
    }

    public Integer volume() { return this.volumePercent; }

    @Override
    public String matchingText() { return this.matchingText; }
}
