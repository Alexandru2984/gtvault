package gtshop.dto;

public class AuthResponse {
    private boolean success;
    private String message;
    private String token;
    private String userId;
    private String name;
    private int activityPoints;

    public AuthResponse() {
    }

    public AuthResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static AuthResponse success(String token, String userId, String name, int activityPoints) {
        AuthResponse resp = new AuthResponse(true, "Authentication successful");
        resp.setToken(token);
        resp.setUserId(userId);
        resp.setName(name);
        resp.setActivityPoints(activityPoints);
        return resp;
    }

    public static AuthResponse error(String message) {
        return new AuthResponse(false, message);
    }

    public boolean isSuccess() {
        return this.success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getToken() {
        return this.token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUserId() {
        return this.userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getActivityPoints() {
        return this.activityPoints;
    }

    public void setActivityPoints(int activityPoints) {
        this.activityPoints = activityPoints;
    }
}

