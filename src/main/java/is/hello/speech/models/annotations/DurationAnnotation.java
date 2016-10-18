package is.hello.speech.models.annotations;


/**
 * Created by ksg on 9/20/16
 */
public class DurationAnnotation implements AnnotationInterface {

    private final String matchingText;
    private final org.joda.time.Duration duration;

    public DurationAnnotation(final String matchingText, final org.joda.time.Duration duration) {
        this.matchingText = matchingText;
        this.duration = duration;
    }

    public org.joda.time.Duration duration() { return this.duration; }

    @Override
    public String matchingText() {
        return this.matchingText;
    }
}
