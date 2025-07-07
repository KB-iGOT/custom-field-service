package com.igot.cb.authentication.util;

import com.igot.cb.authentication.util.CryptoUtil;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.util.AssertionErrors.assertFalse;


class CryptoUtilTest {

    /**
     * Test verifyRSASign method with an invalid algorithm.
     * This test checks if the method returns false when an invalid algorithm is provided.
     */
    @Test
    void test_verifyRSASign_invalidAlgorithm() {
        String payLoad = "test payload";
        byte[] signature = new byte[]{1, 2, 3, 4, 5};
        PublicKey key = null; // We don't need a real key for this test
        String invalidAlgorithm = "InvalidAlgorithm";

        boolean result = CryptoUtil.verifyRSASign(payLoad, signature, key, invalidAlgorithm);
        assertFalse("verifyRSASign should return false for an invalid algorithm", result);
    }

    /**
     * Test verifyRSASign method with an invalid public key.
     * This test checks if the method returns false when an invalid public key is provided.
     */
    @Test
    void test_verifyRSASign_invalidPublicKey() {
        String payLoad = "test payload";
        byte[] signature = new byte[]{1, 2, 3, 4, 5};
        PublicKey invalidKey = null;
        String algorithm = "RSA";

        boolean result = CryptoUtil.verifyRSASign(payLoad, signature, invalidKey, algorithm);
        assertFalse("verifyRSASign should return false for an invalid public key", result);
    }

    /**
     * Tests the verifyRSASign method with a valid signature.
     * This test creates a key pair, signs a payload, and then verifies the signature.
     * It expects the verification to be successful.
     */
    @Test
    void test_verifyRSASign_validSignature() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();

        String payload = "Test payload";
        String algorithm = "SHA256withRSA";

        Signature signature = Signature.getInstance(algorithm);
        signature.initSign(keyPair.getPrivate());
        signature.update(payload.getBytes("US-ASCII"));
        byte[] signatureBytes = signature.sign();

        boolean result = CryptoUtil.verifyRSASign(payload, signatureBytes, publicKey, algorithm);

        assertTrue(result, "Signature verification should be successful");
    }

}
