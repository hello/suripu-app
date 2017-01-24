package is.hello.supichi.db;

import com.hello.suripu.core.db.SenseEventsDAO;
import com.hello.suripu.core.metrics.DeviceEvents;
import org.joda.time.DateTime;

import java.util.Collections;
import java.util.List;

/**
 * Created by ksg on 1/23/17
 */
public class SenseEventsNullDAO implements SenseEventsDAO {
    @Override
    public List<DeviceEvents> get(String s, DateTime dateTime, Integer integer) {
        return Collections.emptyList();
    }

    @Override
    public List<DeviceEvents> get(String s, DateTime dateTime) {
        return Collections.emptyList();
    }

    @Override
    public List<DeviceEvents> getAlarms(String s, DateTime dateTime, DateTime dateTime1) {
        return Collections.emptyList();
    }

    @Override
    public Integer write(List<DeviceEvents> list) {
        return 0;
    }
}
