package is.hello.supichi.db;

import is.hello.supichi.models.HandlerType;

import java.util.List;

/**
 * Created by ksg on 6/20/16
 */
public interface SpeechCommandDAO {
    //Map<String, Action> getActionCommands(HandlerType handlerType);
    List<String> getHandlerCommands(HandlerType handlerType);
}
