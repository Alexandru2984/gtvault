package gtshop.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthRequest {
    @NotBlank(message="Email is required")
    @Email(message="Email must be valid")
    private @NotBlank(message="Email is required") @Email(message="Email must be valid") String email;
    @NotBlank(message="Password is required")
    @Size(min=6, message="Password must be at least 6 characters")
    private @NotBlank(message="Password is required") @Size(min=6, message="Password must be at least 6 characters") String password;
    private String name;

    public String getEmail() {
        return this.email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

