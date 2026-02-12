package com.odp.supportplane.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OTPGeneratorTest {

    @Test
    void generate_returns6Digits() {
        String otp = OTPGenerator.generate();
        assertEquals(6, otp.length());
        assertTrue(otp.matches("\\d{6}"));
    }

    @Test
    void generate_returnsRequestedLength() {
        String otp = OTPGenerator.generate(8);
        assertEquals(8, otp.length());
        assertTrue(otp.matches("\\d{8}"));
    }

    @Test
    void generate_producesUniqueValues() {
        Set<String> otps = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            otps.add(OTPGenerator.generate());
        }
        assertTrue(otps.size() > 90, "Expected mostly unique OTPs, got " + otps.size());
    }

    @Test
    void generate_onlyContainsDigits() {
        for (int i = 0; i < 50; i++) {
            String otp = OTPGenerator.generate();
            for (char c : otp.toCharArray()) {
                assertTrue(Character.isDigit(c));
            }
        }
    }
}
