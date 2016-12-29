package is.hello.supichi.commandhandlers;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import is.hello.supichi.commandhandlers.results.GenericResult;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

public class TriviaHandlerTest {

    class TestCase {
        final List<String> expectedGreetings;
        final LocalDate now;
        final Integer hourOfDay;
        final DateTimeZone timeZone;

        TestCase(final List<String> expectedGreetings, final LocalDate now, final Integer hourOfDay, final DateTimeZone timeZone) {
            this.expectedGreetings = expectedGreetings;
            this.now = now;
            this.hourOfDay = hourOfDay;
            this.timeZone = timeZone;
        }
    }

    @Test
    public void testGreetings() {

        final List<TestCase> testCases = Lists.newArrayList(
                new TestCase(
                        Lists.newArrayList("afternoon"),
                        new LocalDate(2016, 12, 2),
                        12,
                        DateTimeZone.forID("America/Los_Angeles")
                ),
                new TestCase(
                        Lists.newArrayList("morning"),
                        new LocalDate(2016, 12, 2),
                        11,
                        DateTimeZone.forID("America/Los_Angeles")
                ),
                new TestCase(
                        Lists.newArrayList("afternoon"),
                        new LocalDate(1980, 12, 1),
                        12,
                        DateTimeZone.forID("America/Los_Angeles")
                )
        );

        for(final TestCase testCase : testCases) {
            final DateTime now = new DateTime(2017, 1,1, testCase.hourOfDay, 0, 0, testCase.timeZone);
            final GenericResult result = TriviaHandler.greetings(Optional.of(testCase.timeZone.toTimeZone()), now);

            for(String greeting : testCase.expectedGreetings) {
                final String testName = Joiner.on(",").join(testCase.expectedGreetings) + " " + greeting;
                assertTrue(testName, result.responseText().toLowerCase().contains(greeting.toLowerCase()));
            }

        }
    }
}
