package gtshop.model;

public class UserProfile {
    private String id;
    private String name;
    private String avatar;
    private int activityPoints;

    public UserProfile() {}
    public UserProfile(String id, String name, String avatar, int activityPoints) {
        this.id = id; this.name = name; this.avatar = avatar; this.activityPoints = activityPoints;
    }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public int getActivityPoints() { return activityPoints; }
    public void setActivityPoints(int activityPoints) { this.activityPoints = activityPoints; }
}
