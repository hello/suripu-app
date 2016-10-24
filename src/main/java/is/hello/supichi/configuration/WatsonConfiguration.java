package is.hello.supichi.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * Created by ksg on 6/28/16
 */
public class WatsonConfiguration {
    @Valid
    @NotNull
    @JsonProperty("username")
    private String username;
    public String getUsername() { return username; }

    @Valid
    @NotNull
    @JsonProperty("password")
    private String password;
    public String getPassword() { return password; }

    @Valid
    @NotNull
    @JsonProperty("voice")
    private String voiceName;
    public String getVoiceName() { return voiceName; }

}
