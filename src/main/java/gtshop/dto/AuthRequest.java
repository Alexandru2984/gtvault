package gtshop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cerere de inregistrare / autentificare. Validarea detaliata se face in AuthController. */
public class AuthRequest {
    @NotBlank(message = "Email-ul este obligatoriu.")
    @Size(max = 254, message = "Email prea lung.")
    private String email;

    @NotBlank(message = "Parola este obligatorie.")
    @Size(max = 128, message = "Parola este prea lungă.")
    private String password;

    @Size(max = 80, message = "Nume prea lung.")
    private String name;

    @Size(max = 32, message = "Cod de recomandare invalid.")
    private String referralCode;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getReferralCode() { return referralCode; }
    public void setReferralCode(String referralCode) { this.referralCode = referralCode; }
}
