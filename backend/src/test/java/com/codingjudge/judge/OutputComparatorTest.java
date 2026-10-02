package com.codingjudge.judge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Output comparison rules documented in docs/JUDGE_DESIGN.md section 8.1:
 * token-based, ignoring trailing whitespace, trailing newlines,
 * multiple spaces between tokens, and blank lines.
 */
class OutputComparatorTest {

    private final OutputComparator comparator = new OutputComparator();

    @Test
    void identicalOutput_matches() {
        assertThat(comparator.compare("6", "6")).isTrue();
        assertThat(comparator.compare("1 2 3", "1 2 3")).isTrue();
    }

    @Test
    void differentOutput_doesNotMatch() {
        assertThat(comparator.compare("6", "7")).isFalse();
    }

    @Test
    void trailingWhitespace_isIgnored() {
        assertThat(comparator.compare("6", "6   ")).isTrue();
        assertThat(comparator.compare("1 2", "1 2 ")).isTrue();
    }

    @Test
    void trailingNewline_isIgnored() {
        assertThat(comparator.compare("6", "6\n")).isTrue();
        assertThat(comparator.compare("1 2 3\n", "1 2 3")).isTrue();
    }

    @Test
    void leadingWhitespace_isIgnored() {
        assertThat(comparator.compare("  6", "6")).isTrue();
    }

    @Test
    void multipleSpacesBetweenTokens_areNormalized() {
        assertThat(comparator.compare("1 2 3", "1    2     3")).isTrue();
        assertThat(comparator.compare("1 2 3", "1\t2\t3")).isTrue();
    }

    @Test
    void blankLines_areIgnored() {
        assertThat(comparator.compare("1 2 3", "\n1\n\n2\n\n\n3\n")).isTrue();
    }

    @Test
    void multiLineOutput_isComparedAsTokenStream() {
        assertThat(comparator.compare("6\n10", "6\n10")).isTrue();
        assertThat(comparator.compare("6\n10", "6\n  10")).isTrue();
    }

    @Test
    void extraToken_doesNotMatch() {
        assertThat(comparator.compare("1 2", "1 2 3")).isFalse();
    }

    @Test
    void missingToken_doesNotMatch() {
        assertThat(comparator.compare("1 2 3", "1 2")).isFalse();
    }

    @Test
    void tokenOrder_matters() {
        assertThat(comparator.compare("1 2", "2 1")).isFalse();
    }

    @Test
    void partialNumberMatch_doesNotMatch() {
        // Token comparison is exact, not substring based.
        assertThat(comparator.compare("12", "1")).isFalse();
        assertThat(comparator.compare("1", "12")).isFalse();
    }

    @Test
    void emptyExpected_matchesEmptyOutput() {
        assertThat(comparator.compare("", "")).isTrue();
        assertThat(comparator.compare("   ", "")).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "abc, abc",
            "'a b', 'a   b'",
            "'  a  b  ', a b"
    })
    void documentedWhitespaceRules_hold(String expected, String actual) {
        assertThat(comparator.compare(expected, actual)).isTrue();
    }

    @Test
    void nullHandling() {
        assertThat(comparator.compare(null, null)).isTrue();
        assertThat(comparator.compare("6", null)).isFalse();
        assertThat(comparator.compare(null, "6")).isFalse();
    }
}