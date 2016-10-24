package is.hello.supichi.commandhandlers.results;

/**
 * Created by ksg on 9/21/16
 */
public enum Outcome {
    OK("ok"),
    FAIL("fail");

    protected String value;

    Outcome(final String value) {
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }

    public static Outcome fromString(final String text) {
        if (text != null) {
            for (final Outcome outcome : Outcome.values()) {
                if (text.equalsIgnoreCase(outcome.toString()))
                    return outcome;
            }
        }
        return Outcome.FAIL;
    }
}
