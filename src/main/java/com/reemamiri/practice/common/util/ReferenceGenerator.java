package com.reemamiri.practice.common.util;

import java.security.SecureRandom;

/**
 * Short appointment references, e.g. "OT-7K2M9P".
 *
 * Random rather than sequential: a reference is quoted in emails and
 * over the phone, and a counter would let anyone infer how many
 * appointments the practice has taken. The alphabet omits I, O, 0 and 1
 * because these are read aloud and transcribed by hand.
 */
public final class ReferenceGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int LENGTH = 6;

    private ReferenceGenerator() {}

    public static String generate() {
        StringBuilder sb = new StringBuilder("OT-");
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
