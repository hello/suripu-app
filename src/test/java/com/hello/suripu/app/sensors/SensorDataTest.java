package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.hello.suripu.core.models.Sample;
import net.java.quickcheck.Generator;
import net.java.quickcheck.characteristic.AbstractCharacteristic;
import net.java.quickcheck.generator.PrimitiveGenerators;
import org.joda.time.DateTime;
import org.junit.Test;

import java.util.List;

import static net.java.quickcheck.QuickCheck.forAll;

public class SensorDataTest {
    private final ObjectMapper mapper = new ObjectMapper();

    class SampleGenerator implements Generator<Sample>{
        final Generator<Long> longs = PrimitiveGenerators.longs();
        final Generator<Double> doubles = PrimitiveGenerators.doubles();
        final Generator<Integer> ints = PrimitiveGenerators.integers();
        @Override public Sample next() {
            return new Sample(longs.next(), new Float(doubles.next()), ints.next());
        }
    }

    @Test
    public void testNulls() throws JsonProcessingException {
        List<Sample> samples = Lists.newArrayList(
                new Sample(DateTime.now().getMillis(), 10.0f, 0),
                null,
                new Sample(DateTime.now().getMillis(), 10.0f, 0)
        );
        final SensorData sensorData = SensorData.from(samples);
        mapper.writeValueAsString(sensorData);
    }

    @Test
    public void testNegative() throws JsonProcessingException {
        final List<Sample> samples = Lists.newArrayList(
                new Sample(DateTime.now().getMillis(), -110.0f, 0),
                null,
                new Sample(DateTime.now().getMillis(), 0f, 0)
        );
        final SensorData sensorData = SensorData.from(samples);
        mapper.writeValueAsString(sensorData);
    }

    @Test
    public void samples() {
        forAll(new SampleGenerator(), new AbstractCharacteristic<Sample>() {
            @Override
            protected void doSpecify(Sample any) {
                final SensorData sensorData = SensorData.from(Lists.newArrayList(any));
                try {
                    mapper.writeValueAsString(sensorData);
                } catch (Exception e) {

                    throw new RuntimeException(e.getMessage());
                }

            }
        });
    }
}
