package is.hello.supichi.models;

/**
 * Created by ksg on 8/23/16
 */
public enum UploadResponseType {
    ADPCM("adpcm", 0),
    MP3("mp3", 1);

    private String name;
    private int value;

    private UploadResponseType(String name, int value) {
    }

    public static UploadResponseType fromString(String text) {
        if (text != null) {
            for (UploadResponseType responseType : values()) {
                if (text.equalsIgnoreCase(responseType.name())) {
                    return responseType;
                }
            }
        }

        throw new IllegalArgumentException("No constant with text " + text + " found");
    }
}
