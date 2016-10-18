package is.hello.speech.utils;

import javax.servlet.http.HttpServletRequest;

public class Metadata {
    public static String getIpAddress(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For") == null?request.getRemoteAddr():request.getHeader("X-Forwarded-For");
        if(ipAddress == null) {
            return "";
        } else {
            String[] ipAddresses = ipAddress.split(",");
            return ipAddresses[0];
        }
    }
}
