package com.matcher.matchmate_backend.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class IdentityVerificationUtil {

    private IdentityVerificationUtil() {}

    private static final Pattern PAN_PATTERN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");
    private static final Pattern AADHAAR_PATTERN = Pattern.compile("^[2-9][0-9]{11}$");

    // Verhoeff multiplication table
    private static final int[][] D = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
            {1, 2, 3, 4, 0, 6, 7, 8, 9, 5},
            {2, 3, 4, 0, 1, 7, 8, 9, 5, 6},
            {3, 4, 0, 1, 2, 8, 9, 5, 6, 7},
            {4, 0, 1, 2, 3, 9, 5, 6, 7, 8},
            {5, 9, 8, 7, 6, 0, 4, 3, 2, 1},
            {6, 5, 9, 8, 7, 1, 0, 4, 3, 2},
            {7, 6, 5, 9, 8, 2, 1, 0, 4, 3},
            {8, 7, 6, 5, 9, 3, 2, 1, 0, 4},
            {9, 8, 7, 6, 5, 4, 3, 2, 1, 0}
    };

    // Verhoeff permutation table
    private static final int[][] P = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
            {1, 5, 7, 6, 2, 8, 3, 0, 9, 4},
            {5, 8, 0, 3, 7, 9, 6, 1, 4, 2},
            {8, 9, 1, 6, 0, 4, 3, 5, 2, 7},
            {9, 4, 5, 3, 1, 2, 6, 8, 7, 0},
            {4, 2, 8, 6, 5, 7, 3, 9, 0, 1},
            {2, 7, 9, 3, 8, 0, 6, 4, 1, 5},
            {7, 0, 4, 6, 9, 1, 3, 2, 5, 8}
    };

    /**
     * Validates Aadhaar format and Verhoeff checksum.
     * Aadhaar must be 12 numeric digits, cannot begin with 0 or 1, and checksum must equal 0.
     */
    public static boolean validateAadhaar(String aadhaar) {
        if (aadhaar == null) return false;
        String clean = aadhaar.replaceAll("\\s+", "").trim();
        if (!AADHAAR_PATTERN.matcher(clean).matches()) {
            return false;
        }

        int c = 0;
        int len = clean.length();
        for (int i = 0; i < len; i++) {
            int digit = Character.getNumericValue(clean.charAt(len - 1 - i));
            c = D[c][P[i % 8][digit]];
        }
        return c == 0;
    }

    /**
     * Masks Aadhaar into format "XXXX XXXX 1234".
     */
    public static String maskAadhaar(String aadhaar) {
        if (aadhaar == null) return "";
        String clean = aadhaar.replaceAll("\\s+", "").trim();
        if (clean.length() < 4) return "XXXX XXXX XXXX";
        String last4 = clean.substring(clean.length() - 4);
        return "XXXX XXXX " + last4;
    }

    /**
     * Validates PAN format (5 letters, 4 digits, 1 letter).
     */
    public static boolean validatePan(String pan) {
        if (pan == null) return false;
        String clean = pan.replaceAll("\\s+", "").trim().toUpperCase(Locale.ENGLISH);
        return PAN_PATTERN.matcher(clean).matches();
    }

    /**
     * Masks PAN into format "XXXXX1234X".
     */
    public static String maskPan(String pan) {
        if (pan == null) return "";
        String clean = pan.replaceAll("\\s+", "").trim().toUpperCase(Locale.ENGLISH);
        if (clean.length() != 10) return "XXXXXXXXXX";
        return "XXXXX" + clean.substring(5, 9) + clean.charAt(9);
    }

    /**
     * Validates whether the name on the document matches the profile name.
     * Checks if at least one significant name token (length >= 2) from the document
     * exists in the profile name (case-insensitive).
     */
    public static boolean matchNames(String docName, String profileName) {
        if (docName == null || profileName == null) return false;
        String[] docTokens = docName.toLowerCase(Locale.ENGLISH).trim().split("[\\s._-]+");
        String[] profileTokens = profileName.toLowerCase(Locale.ENGLISH).trim().split("[\\s._-]+");

        Set<String> profileTokenSet = new HashSet<>(Arrays.asList(profileTokens));
        int matches = 0;
        int validDocTokens = 0;

        for (String token : docTokens) {
            if (token.length() >= 2) {
                validDocTokens++;
                if (profileTokenSet.contains(token)) {
                    matches++;
                }
            }
        }
        // At least one valid token matches
        return validDocTokens > 0 && matches >= 1;
    }
}
