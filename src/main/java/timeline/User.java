package timeline;

import security.KeyFingerprinter;

import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Objects;

public class User implements Serializable {
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

    public PublicKey getPublicKey ( String algorithm ) {
        try {
            return PeerKeys.PersistentKey.<PublicKey>fromString( algorithm, PeerKeys.KeyType.Public, this.getPassword() ).get();
        } catch ( InvalidKeySpecException | NoSuchAlgorithmException e ) {
            e.printStackTrace();

            return null;
        }
    }

    public String getPublicKeyFingerprint ( String algorithm ) {
        return KeyFingerprinter.md5Fingerprint( this.getPublicKey( algorithm ) );
    }
}
