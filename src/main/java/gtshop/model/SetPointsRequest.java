package gtshop.model;

public class SetPointsRequest {
    private int points;

    public SetPointsRequest() {
    }

    public SetPointsRequest(int points) {
        this.points = points;
    }

    public int getPoints() {
        return this.points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public String toString() {
        return "SetPointsRequest{points=" + this.points + "}";
    }
}

