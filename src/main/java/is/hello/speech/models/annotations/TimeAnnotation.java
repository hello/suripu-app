package is.hello.speech.models.annotations;

import org.joda.time.DateTime;


/**
 * Created by ksg on 9/20/16
 */
public class TimeAnnotation implements AnnotationInterface {

    private final String matchingText;
    private final DateTime dateTime;

    public TimeAnnotation(final String matchingText, final DateTime dateTime) {
        this.matchingText = matchingText;
        this.dateTime = dateTime;
    }

    public DateTime dateTime() { return this.dateTime; }

    @Override
    public String matchingText() {
        return this.matchingText;
    }
}
