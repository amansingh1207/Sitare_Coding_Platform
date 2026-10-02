package com.codingjudge.judge;

import org.springframework.stereotype.Component;

@Component
public class OutputComparator {

    /**
     * Token-based comparison:
     * - Split both strings on whitespace
     * - Compare token by token
     * - Ignores trailing/leading whitespace, multiple spaces, blank lines
     */
    public static boolean compare(String expected, String actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        String[] expectedTokens = expected.trim().split("\\s+");
        String[] actualTokens = actual.trim().split("\\s+");

        if (expectedTokens.length != actualTokens.length) {
            return false;
        }
        for (int i = 0; i < expectedTokens.length; i++) {
            if (!expectedTokens[i].equals(actualTokens[i])) {
                return false;
            }
        }
        return true;
    }
}