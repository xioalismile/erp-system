package com.erp.license.validator;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class KeyPairValidator {
    public static void main(String[] args) throws Exception {
        String pubKeyStr = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("public_key_base64.txt"))).trim();
        String privKeyStr = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("private_key_base64.txt"))).trim();

        byte[] pubBytes = Base64.getDecoder().decode(pubKeyStr);
        byte[] privBytes = Base64.getDecoder().decode(privKeyStr);

        KeyFactory kf = KeyFactory.getInstance("RSA");
        RSAPublicKey pubKey = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(pubBytes));
        RSAPrivateKey privKey = (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));

        System.out.println("Public key modulus:  " + pubKey.getModulus());
        System.out.println("Private key modulus: " + privKey.getModulus());
        System.out.println("Keys match: " + pubKey.getModulus().equals(privKey.getModulus()));

        // Test sign + verify
        String test = "test-payload";
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privKey);
        sig.update(test.getBytes("UTF-8"));
        byte[] signed = sig.sign();
        String sigBase64 = Base64.getEncoder().encodeToString(signed);

        sig.initVerify(pubKey);
        sig.update(test.getBytes("UTF-8"));
        boolean verified = sig.verify(Base64.getDecoder().decode(sigBase64));

        System.out.println("Sign+Verify test: " + (verified ? "PASS" : "FAIL"));
    }
}
