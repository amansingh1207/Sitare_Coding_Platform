package com.codingjudge.judge;

import com.codingjudge.model.entity.Problem;
import com.codingjudge.model.entity.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Mirrors CodingJudge problems into DOMjudge (Phase 9 design).
 *
 * <p>DOMjudge judges whole submissions against its own test data, so each
 * CodingJudge problem is imported once as a DOMjudge problem and then
 * referenced by id. Identity is {@code externalid = "codingjudge-<id>-<hash>"}
 * where the hash covers sorted test data + limits: editing a problem yields
 * a new DOMjudge problem instead of silently judging against stale data
 * (re-import appends testcases; old mirrors are left for an admin to prune).
 * Lookup-first makes restarts safe: an existing mirror is reused, never
 * duplicated.
 */
public class DomjudgeProblemMirror {

    private static final Logger LOG = LoggerFactory.getLogger(DomjudgeProblemMirror.class);

    private final DomjudgeClient client;
    private final String contest;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public DomjudgeProblemMirror(DomjudgeClient client, String contest) {
        this.client = client;
        this.contest = contest;
    }

    /**
     * Returns the DOMjudge problem id for OUR problem, importing first if
     * needed. The returned id is stable per problem content.
     */
    public String ensure(Problem problem) {
        String key = mirrorKey(problem);
        String cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        String shortName = "cj-" + problem.getId() + "-" + contentHash(problem);
        for (DomjudgeClient.ProblemInfo info : client.listProblems(contest)) {
            if (shortName.equals(info.shortName()) || shortName.equals(info.id())) {
                cache.put(key, info.id());
                return info.id();
            }
        }
        LOG.info("Importing problem {} as DOMjudge problem {}", problem.getId(), shortName);
        String created = client.importProblem(contest, buildPackage(problem, shortName));
        cache.put(key, created);
        return created;
    }

    static String mirrorKey(Problem problem) {
        return problem.getId() + ":" + contentHash(problem);
    }

    /** Short hash over sorted test data + limits: edits change the mirror. */
    static String contentHash(Problem problem) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            StringBuilder canonical = new StringBuilder();
            canonical.append(problem.getTimeLimitMs()).append('|')
                    .append(problem.getMemoryLimitMb()).append('|');
            orderedCases(problem).forEach(testCase -> canonical
                    .append(testCase.getInputData()).append('\0')
                    .append(testCase.getExpectedOutput()).append('\0')
                    .append(Boolean.TRUE.equals(testCase.getSample())).append('\n'));
            byte[] hash = digest.digest(
                    canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }

    /** Deterministic file order: samples first (by sortOrder), then hidden. */
    static List<TestCase> orderedCases(Problem problem) {
        List<TestCase> samples = new ArrayList<>();
        List<TestCase> hidden = new ArrayList<>();
        for (TestCase testCase : problem.getTestCases()) {
            (Boolean.TRUE.equals(testCase.getSample()) ? samples : hidden).add(testCase);
        }
        Comparator<TestCase> byOrder = Comparator.comparing(
                TestCase::getSortOrder, Comparator.nullsLast(Integer::compareTo));
        samples.sort(byOrder);
        hidden.sort(byOrder);
        List<TestCase> ordered = new ArrayList<>(samples);
        ordered.addAll(hidden);
        return ordered;
    }

    /** ICPC-style package DOMjudge imports (proven against 9.0.0 live). */
    static byte[] buildPackage(Problem problem, String shortName) {
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(buf)) {
                int sample = 0;
                int secret = 0;
                for (TestCase testCase : orderedCases(problem)) {
                    String dir;
                    int number;
                    if (Boolean.TRUE.equals(testCase.getSample())) {
                        dir = "sample";
                        number = ++sample;
                    } else {
                        dir = "secret";
                        number = ++secret;
                    }
                    String base = "data/" + dir + "/" + number;
                    writeEntry(zip, base + ".in", orEmpty(testCase.getInputData()));
                    writeEntry(zip, base + ".ans", orEmpty(testCase.getExpectedOutput()));
                }
                String title = problem.getTitle() == null ? shortName : problem.getTitle();
                writeEntry(zip, "problem.yaml", "name: " + title.replace("\n", " ") + "\n");
                int timeLimitSec = problem.getTimeLimitMs() == null
                        ? 2 : Math.max(1, problem.getTimeLimitMs() / 1000);
                writeEntry(zip, "domjudge-problem.ini",
                        "short-name = " + shortName + "\n"
                                + "timelimit = " + timeLimitSec + "\n");
            }
            return buf.toByteArray();
        } catch (IOException e) {
            throw new DomjudgeClient.DomjudgeException("Failed to build problem package", e);
        }
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void writeEntry(ZipOutputStream zip, String name, String content)
            throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
