package varga.supportplane.security;

import java.security.SecureRandom;

public class OTPGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private OTPGenerator() {}

    public static String generate(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    public static String generate() {
        return generate(6);
    }
}
