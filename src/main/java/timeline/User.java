package timeline;

public class User {
    private String username;
    private String publicKey;
    private int activity;

    public User(String username, String publicKey) {
        this.username = username;
        this.publicKey = publicKey;
        this.activity = 0;
    }

    public User(String username, String publicKey, int activity) {
        this.username = username;
        this.publicKey = publicKey;
        this.activity = activity;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return publicKey;
    }

    public void setPassword(String publicKey) {
        this.publicKey = publicKey;
    }

    public int getActivity() {
        return activity;
    }

    public void setActivity(int activity) {
        this.activity = activity;
    }
}
