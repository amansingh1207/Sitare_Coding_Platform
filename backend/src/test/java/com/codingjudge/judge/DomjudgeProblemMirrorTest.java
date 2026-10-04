package com.codingjudge.judge;

import com.codingjudge.model.entity.Problem;
import com.codingjudge.model.entity.TestCase;
import com.codingjudge.model.enums.Difficulty;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DomjudgeProblemMirror}: package bytes, stable identity,
 * lookup-before-import and caching. A fake client subclass stands in for HTTP.
 */
class DomjudgeProblemMirrorTest {

    static class FakeClient extends DomjudgeClient {
        final List<DomjudgeClient.ProblemInfo> known = new ArrayList<>();
        final AtomicInteger imports = new AtomicInteger();
        volatile String lastZipFilename;

        FakeClient() {
            super("http://domjudge.invalid", "u", "p");
        }

        @Override
        public List<DomjudgeClient.ProblemInfo> listProblems(String contest) {
            return List.copyOf(known);
        }

        @Override
        public String importProblem(String contest, String zipFilename, byte[] packageZip) {
            imports.incrementAndGet();
            lastZipFilename = zipFilename;
            return "imported-" + imports.get();
        }
    }

    private Problem problem(long id, int samples, int hidden) {
        Problem problem = new Problem();
        problem.setId(id);
        problem.setSlug("sum-" + id);
        problem.setTitle("Sum " + id);
        problem.setStatement("Add.");
        problem.setInputFormat("Ints.");
        problem.setOutputFormat("Sum.");
        problem.setDifficulty(Difficulty.EASY);
        problem.setWeekLabel("Week 1");
        problem.setTimeLimitMs(2000);
        problem.setMemoryLimitMb(256);
        int order = 0;
        for (int i = 0; i < samples; i++) {
            problem.addTestCase(testCase(100L + i, "3 4", "7", true, order++));
        }
        for (int i = 0; i < hidden; i++) {
            problem.addTestCase(testCase(200L + i, "1 1", "2", false, order++));
        }
        return problem;
    }

    private TestCase testCase(long id, String input, String expected, boolean sample, int order) {
        TestCase testCase = new TestCase();
        testCase.setId(id);
        testCase.setInputData(input);
        testCase.setExpectedOutput(expected);
        testCase.setSample(sample);
        testCase.setSortOrder(order);
        return testCase;
    }

    @Test
    void packageContainsSamplesSecretsAndMetadata() throws Exception {
        byte[] zip = DomjudgeProblemMirror.buildPackage(problem(3L, 1, 2), "cj-3-ab12cd34");

        Set<String> names = new HashSet<>();
        String ini = null;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                names.add(entry.getName());
                if (entry.getName().equals("domjudge-problem.ini")) {
                    ini = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }

        assertThat(names).contains(
                "problem.yaml", "domjudge-problem.ini",
                "data/sample/1.in", "data/sample/1.ans",
                "data/secret/1.in", "data/secret/1.ans",
                "data/secret/2.in", "data/secret/2.ans");
        assertThat(ini).contains("short-name = cj-3-ab12cd34").contains("timelimit = 2");
    }

    @Test
    void contentHashChangesWhenTestDataChanges() {
        String before = DomjudgeProblemMirror.contentHash(problem(3L, 1, 2));
        Problem edited = problem(3L, 1, 2);
        edited.getTestCases().get(0).setExpectedOutput("8");

        assertThat(DomjudgeProblemMirror.contentHash(edited)).isNotEqualTo(before);
    }

    @Test
    void lookupHitSkipsImport() {
        FakeClient client = new FakeClient();
        DomjudgeProblemMirror mirror = new DomjudgeProblemMirror(client, "demo");
        Problem problem = problem(3L, 1, 1);
        mirror.ensure(problem);
        client.known.add(new DomjudgeClient.ProblemInfo("dom-9", "cj-3-" + DomjudgeProblemMirror.contentHash(problem), "x"));
        int importsBefore = client.imports.get();

        DomjudgeProblemMirror fresh = new DomjudgeProblemMirror(client, "demo");
        assertThat(fresh.ensure(problem)).isEqualTo("dom-9");
        assertThat(client.imports.get()).isEqualTo(importsBefore);
    }

    @Test
    void secondEnsureHitsCacheWithoutListing() {
        FakeClient client = new FakeClient() {
            int lists;

            @Override
            public List<DomjudgeClient.ProblemInfo> listProblems(String contest) {
                lists++;
                return super.listProblems(contest);
            }
        };
        DomjudgeProblemMirror mirror = new DomjudgeProblemMirror(client, "demo");
        Problem problem = problem(5L, 1, 0);

        String first = mirror.ensure(problem);
        String second = mirror.ensure(problem);

        assertThat(second).isEqualTo(first);
        assertThat(client.imports.get()).isEqualTo(1);
    }

    @Test
    void orderedCasesPutsSamplesFirst() {
        List<TestCase> ordered = DomjudgeProblemMirror.orderedCases(problem(9L, 2, 2));

        assertThat(ordered).extracting(TestCase::getSample)
                .containsExactly(true, true, false, false);
    }

    @Test
    void importZipFilenameCarriesShortNameForUniqueExternalId() {
        // DOMjudge derives problem.externalid from the uploaded ZIP filename:
        // a fixed name collides on the second import. The mirror must send
        // the short-name as the filename (verified live).
        FakeClient client = new FakeClient();
        DomjudgeProblemMirror mirror = new DomjudgeProblemMirror(client, "demo");
        Problem problem = problem(3L, 1, 1);

        mirror.ensure(problem);

        String expected = "cj-3-" + DomjudgeProblemMirror.contentHash(problem) + ".zip";
        assertThat(client.lastZipFilename).isEqualTo(expected);
    }
}
