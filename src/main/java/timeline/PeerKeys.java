package timeline;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

public class PeerKeys {
    public enum KeyType {
        Public,

        Private
    }

    public static class PersistentKey<K extends Key> {
        protected String getHeader () {
            return String.format( "-----BEGIN %s %s KEY-----\n", this.algorithm, this.type == KeyType.Private ? "PRIVATE" : "PUBLIC" );
        }

        protected String getFooter () {
            return String.format( "\n-----END %s %s KEY-----\n", this.algorithm, this.type == KeyType.Private ? "PRIVATE" : "PUBLIC" );
        }

        protected String algorithm;

        protected KeyType type;

        protected String file;

        protected K key = null;

        public PersistentKey ( String algorithm, KeyType type, String file ) {
            this.algorithm = algorithm;
            this.type = type;
            this.file = file;
        }

        public boolean has () {
            return this.key != null;
        }

        public K get () {
            return this.key;
        }

        public void set ( K key ) {
            this.key = key;
        }

        public boolean hasFile () {
            return new File( this.file ).exists();
        }

        // https://www.novixys.com/blog/how-to-generate-rsa-keys-java/
        @SuppressWarnings( "unchecked" )
        public void load () throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
            int headerLength = this.getHeader().getBytes().length;
            int footerLength = this.getFooter().getBytes().length;

            byte[] bytes = Files.readAllBytes( Paths.get( this.file ) );

            Base64.Decoder decoder = Base64.getDecoder();

            byte[] keyBytes = Arrays.copyOfRange(bytes, headerLength, bytes.length - footerLength);

            if ( this.type == KeyType.Private ) {
                PKCS8EncodedKeySpec ks = new PKCS8EncodedKeySpec( decoder.decode( keyBytes ) );
                KeyFactory kf = KeyFactory.getInstance( this.algorithm );
                this.key = (K)kf.generatePrivate( ks );
            } else {
                X509EncodedKeySpec ks = new X509EncodedKeySpec( decoder.decode( keyBytes ) );
                KeyFactory kf = KeyFactory.getInstance( this.algorithm );
                this.key = (K)kf.generatePublic( ks );
            }
        }

        public void save () throws IOException {
            Base64.Encoder encoder = Base64.getEncoder();
            
            Writer out = new FileWriter( this.file );
            out.write( this.getHeader() );
            out.write( encoder.encodeToString( this.key.getEncoded() ) );
            out.write( this.getFooter() );
            out.close();
        }
    }


    public static String keysFile = "./identity";

    protected String algorithm = "DSA";

    protected PersistentKey<PublicKey> publicKey = new PersistentKey<>( this.algorithm, KeyType.Public, keysFile + ".pub" );

    protected PersistentKey<PrivateKey> privateKey = new PersistentKey<>( this.algorithm, KeyType.Private, keysFile + ".key" );

    public void generateIdentityKeys () throws NoSuchAlgorithmException, IOException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance( this.algorithm );

        kpg.initialize(2048);

        KeyPair kp = kpg.generateKeyPair();

        this.publicKey.set( kp.getPublic() );
        this.privateKey.set( kp.getPrivate() );

//        System.err.println("Private key format: " + kp.getPrivate().getFormat());
//        System.err.println("Public key format: " + kp.getPublic().getFormat());

        this.publicKey.save();
        this.privateKey.save();
    }

    public boolean hasIdentityKeys () {
        return this.publicKey.hasFile() && this.privateKey.hasFile();
    }

    public void loadIdentityKeys () throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        this.privateKey.load();
        this.publicKey.load();
    }

    public void init () throws Exception {
        if ( this.hasIdentityKeys() ) {
            this.loadIdentityKeys();
        } else {
            this.generateIdentityKeys();
        }
    }
}
