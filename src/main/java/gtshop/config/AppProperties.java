package gtshop.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Setari generale ale aplicatiei (prefix {@code rewardhub.*}).
 */
@ConfigurationProperties(prefix = "rewardhub")
public class AppProperties {

    /** Fusul orar folosit pentru "azi" (streak, quiz, limite zilnice). */
    private String timezone = "Europe/Bucharest";

    /** URL-ul public al site-ului, folosit pentru link-uri de recomandare. */
    private String publicBaseUrl = "https://shop.micutu.com";

    /** Originile acceptate pentru CORS (frontend-ul principal este same-origin). */
    private List<String> corsOrigins = new ArrayList<>();

    /** Email-urile care primesc automat rolul ADMIN la pornire / autentificare. */
    private List<String> adminEmails = new ArrayList<>();

    /**
     * Daca este true, IP-ul clientului se citeste din X-Real-IP doar cand cererea vine de pe loopback
     * (nginx local). Niciodata nu se ia in calcul pentru cereri directe.
     */
    private boolean trustProxyHeaders = true;

    /**
     * Director optional cu continut privat (quiz.json, fortunes.json, wordle.json). Repository-ul este public,
     * deci in productie intrebarile si raspunsurile pot fi tinute in afara codului sursa.
     */
    private String contentDir = "";

    /** Secretul pentru alegerea cuvantului zilei; daca lipseste se foloseste JWT_SECRET. */
    private String gamesSecret = "";

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl != null && publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    public List<String> getCorsOrigins() { return corsOrigins; }
    public void setCorsOrigins(List<String> corsOrigins) {
        this.corsOrigins = corsOrigins == null ? new ArrayList<>() :
                corsOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public List<String> getAdminEmails() { return adminEmails; }
    public void setAdminEmails(List<String> adminEmails) {
        this.adminEmails = adminEmails == null ? new ArrayList<>() :
                adminEmails.stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).toList();
    }

    public boolean isAdminEmail(String email) {
        return email != null && adminEmails.contains(email.trim().toLowerCase(Locale.ROOT));
    }

    public String getContentDir() { return contentDir; }
    public void setContentDir(String contentDir) { this.contentDir = contentDir == null ? "" : contentDir.trim(); }

    public String getGamesSecret() { return gamesSecret; }
    public void setGamesSecret(String gamesSecret) { this.gamesSecret = gamesSecret == null ? "" : gamesSecret; }

    public boolean isTrustProxyHeaders() { return trustProxyHeaders; }
    public void setTrustProxyHeaders(boolean trustProxyHeaders) { this.trustProxyHeaders = trustProxyHeaders; }
}
