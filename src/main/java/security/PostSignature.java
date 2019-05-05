package security;

import java.security.*;

public class PostSignature {

    public byte[] sign(byte[] data, PrivateKey privateKey) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        SecureRandom secureRandom = new SecureRandom();
        Signature signature = Signature.getInstance("SHA256WithDSA");

        signature.initSign(privateKey, secureRandom);
        signature.update(data);
        return signature.sign();
    }

    public boolean verify(byte[] data, byte[] digitalSignature, PublicKey publicKey) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature signature = Signature.getInstance("SHA256WithDSA");

        signature.initVerify(publicKey);
        signature.update(data);
        return signature.verify(digitalSignature);
    }
}
