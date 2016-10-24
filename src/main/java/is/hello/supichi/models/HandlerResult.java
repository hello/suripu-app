package is.hello.supichi.models;

import com.google.common.base.Optional;
import is.hello.supichi.commandhandlers.results.GenericResult;
import is.hello.supichi.commandhandlers.results.HueResult;
import is.hello.supichi.commandhandlers.results.NestResult;
import is.hello.supichi.commandhandlers.results.Outcome;
import is.hello.supichi.commandhandlers.results.RoomConditionResult;

/**
 * Created by ksg on 7/25/16
 */
public class HandlerResult {
    public static final String EMPTY_COMMAND = "NONE";
    public static final String EMPTY_STRING = "";

    public final HandlerType handlerType;
    public final String command;

    // all the handler results
    public final Optional<GenericResult> optionalResult;
    public final Optional <RoomConditionResult> optionalRoomResult;
    public final Optional<HueResult> optionalHueResult;
    public final Optional<NestResult> optionalNestResult;

    // for trivia s3 file marker
    public final Optional<String> fileMarker;

    public HandlerResult(final HandlerType handlerType, final String command,
                         final Optional<GenericResult> optionalResult,
                         final Optional<RoomConditionResult> optionalRoomResult,
                         final Optional<HueResult> optionalHueResult,
                         final Optional<NestResult> optionalNestResult,
                         final Optional<String> fileMarker
    ) {
        this.handlerType = handlerType;
        this.command = command;
        this.optionalResult = optionalResult;
        this.optionalRoomResult = optionalRoomResult;
        this.optionalHueResult = optionalHueResult;
        this.optionalNestResult = optionalNestResult;
        this.fileMarker = fileMarker;

    }

    public HandlerResult(final HandlerType handlerType, final String command, final GenericResult result) {
        this.handlerType = handlerType;
        this.command = command;
        this.optionalResult = Optional.of(result);
        this.optionalRoomResult = Optional.absent();
        this.optionalHueResult = Optional.absent();
        this.optionalNestResult = Optional.absent();
        this.fileMarker = Optional.absent();
    }


    public Outcome outcome() {
        if (optionalResult.isPresent()) {
            return optionalResult.get().outcome;
        }
        return Outcome.FAIL;
    }

    public String responseText() {
        if (optionalResult.isPresent()) {
            return optionalResult.get().responseText();
        }
        return EMPTY_STRING;
    }

    public Optional<String> optionalErrorText() {
        if (optionalResult.isPresent() && optionalResult.get().errorText.isPresent()) {
            return optionalResult.get().errorText;
        }
        return Optional.absent();
    }

    public static HandlerResult emptyResult() {
        return new HandlerResult(HandlerType.NONE, EMPTY_COMMAND, Optional.absent(), Optional.absent(), Optional.absent(), Optional.absent(), Optional.absent());
    }

    public static HandlerResult withRoomResult(final HandlerType handlerType, final String command, final GenericResult result, final RoomConditionResult roomResult) {
        return new HandlerResult(handlerType, command, Optional.of(result), Optional.of(roomResult), Optional.absent(), Optional.absent(), Optional.absent());
    }

    public static HandlerResult withFileMarker(final HandlerType handlerType, final String command, final GenericResult result, final String marker) {
        return new HandlerResult(handlerType, command, Optional.of(result), Optional.absent(), Optional.absent(), Optional.absent(), Optional.of(marker));
    }

    public static HandlerResult withHueResult(final HandlerType handlerType, final String command, final GenericResult result, final HueResult hueResult) {
        return new HandlerResult(handlerType, command, Optional.of(result), Optional.absent(), Optional.of(hueResult), Optional.absent(), Optional.absent());
    }

    public static HandlerResult withNestResult(final HandlerType handlerType, final String command, final GenericResult result, final NestResult nestResult) {
        return new HandlerResult(handlerType, command, Optional.of(result), Optional.absent(), Optional.absent(), Optional.of(nestResult), Optional.absent());
    }

}
