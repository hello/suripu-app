package is.hello.speech.commandhandlers.results;

/**
 * Created by ksg on 9/21/16
 */
public class HueResult {

    public final String lightOn;
    public final String brightnessAdjust;
    public final String colorTempAdjust;


    public HueResult(final String lightOn, final String brightnessAdjust, final String colorTempAdjust) {
        this.lightOn = lightOn;
        this.brightnessAdjust = brightnessAdjust;
        this.colorTempAdjust = colorTempAdjust;
    }
}
