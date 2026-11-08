package gtshop.security;

import gtshop.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import org.springframework.stereotype.Component;

/**
 * Determina IP-ul real al clientului. Header-ul X-Real-IP (setat de nginx dupa validarea
 * CF-Connecting-IP) este de incredere DOAR daca cererea vine de pe loopback.
 */
@Component
public class ClientIpResolver {
    private final boolean trustProxy;

    public ClientIpResolver(AppProperties props) {
        this.trustProxy = props.isTrustProxyHeaders();
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (trustProxy && isLoopback(remote)) {
            String real = request.getHeader("X-Real-IP");
            if (real != null && !real.isBlank() && real.length() <= 64 && real.matches("[0-9a-fA-F:.]+")) {
                return real.trim();
            }
        }
        return remote;
    }

    private static boolean isLoopback(String addr) {
        if (addr == null) return false;
        if (addr.startsWith("127.") || addr.equals("::1") || addr.equals("0:0:0:0:0:0:0:1")) return true;
        try {
            return InetAddress.getByName(addr).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }
}
