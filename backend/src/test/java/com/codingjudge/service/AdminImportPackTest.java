package com.codingjudge.service;

import com.codingjudge.model.dto.response.AdminProblemSummary;
import com.codingjudge.model.dto.response.ImportPackResponse;
import com.codingjudge.model.dto.response.ImportedProblemSummary;
import com.codingjudge.model.entity.Problem;
import com.codingjudge.model.entity.TestCase;
import com.codingjudge.model.enums.Difficulty;
import com.codingjudge.repository.ProblemRepository;
import com.codingjudge.repository.TestCaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the streaming problem-pack import: statements, sample/hidden
 * pairing (including an unpaired .in that must be ignored), skip-on-reimport
 * and invalid packs. Uses small data; the streaming behavior itself (bounded
 * memory for huge packs) follows from reading one entry at a time.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminImportPackTest {

    private static final String ALPHA_STATEMENT =
            "<header><h1>Alpha Problem</h1>"
                    + "<p class=\"meta\">Difficulty: easy. Time limit: 2 seconds.</p></header>"
                    + "<p>Story of alpha.</p>"
                    + "<h2>Input</h2><p>An integer.</p>"
                    + "<h2>Output</h2><p>Double it.</p>"
                    + "<h2>Examples</h2><p>1 gives 2.</p>";

    private static final String BETA_STATEMENT =
            "<h1>Beta Problem</h1>"
                    + "<p>Story of beta.</p>"
                    + "<h2>Input</h2><p>A word.</p>"
                    + "<h2>Output</h2><p>Echo it.</p>"
                    + "<h2>Examples</h2><p>hi gives hi.</p>";

    @Autowired
    private AdminService adminService;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Test
    void importCreatesProblemsSamplesAndHidden() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("pack/alpha/statement.html", ALPHA_STATEMENT);
        files.put("pack/alpha/tests/sample/a.in", "5");
        files.put("pack/alpha/tests/sample/a.ans", "10");
        files.put("pack/alpha/tests/secret/b.in", "7");
        files.put("pack/alpha/tests/secret/b.ans", "14");
        files.put("pack/alpha/tests/secret/orphan.in", "9");
        files.put("pack/beta/statement.html", BETA_STATEMENT);
        files.put("pack/beta/tests/sample/1.in", "hi");
        files.put("pack/beta/tests/sample/1.ans", "hi");

        ImportPackResponse response = importPack(files, "Week 5");

        assertThat(response.getTotalProblems()).isEqualTo(2);
        assertThat(response.getTotalSamples()).isEqualTo(2);
        assertThat(response.getTotalHidden()).isEqualTo(1);
        List<ImportedProblemSummary> summaries = response.getProblems();
        assertThat(summaries.get(0).getSlug()).isEqualTo("alpha");
        assertThat(summaries.get(0).getStatus()).isEqualTo("CREATED");
        assertThat(summaries.get(0).getSampleCount()).isEqualTo(1);
        assertThat(summaries.get(0).getHiddenCount()).isEqualTo(1);
        assertThat(summaries.get(1).getSlug()).isEqualTo("beta");

        Problem alpha = problemRepository.findBySlug("alpha").orElseThrow();
        assertThat(alpha.getTitle()).isEqualTo("Alpha Problem");
        assertThat(alpha.getDifficulty()).isEqualTo(Difficulty.EASY);
        assertThat(alpha.getTimeLimitMs()).isEqualTo(2000);
        assertThat(alpha.getWeekLabel()).isEqualTo("Week 5");
        assertThat(alpha.getInputFormat()).isEqualTo("An integer.");
        assertThat(alpha.getOutputFormat()).isEqualTo("Double it.");

        List<TestCase> cases = testCaseRepository
                .findByProblemIdOrderBySortOrderAsc(alpha.getId());
        assertThat(cases).hasSize(2);
        assertThat(cases.get(0).getSample()).isTrue();
        assertThat(cases.get(0).getSortOrder()).isEqualTo(0);
        assertThat(cases.get(0).getInputData()).isEqualTo("5");
        assertThat(cases.get(0).getExpectedOutput()).isEqualTo("10");
        assertThat(cases.get(1).getSample()).isFalse();
        assertThat(cases.get(1).getSortOrder()).isEqualTo(1);
        assertThat(cases.get(1).getInputData()).isEqualTo("7");
        assertThat(cases.get(1).getExpectedOutput()).isEqualTo("14");

        Problem beta = problemRepository.findBySlug("beta").orElseThrow();
        assertThat(beta.getDifficulty()).isEqualTo(Difficulty.MEDIUM);
        assertThat(beta.getTimeLimitMs()).isEqualTo(2000);
    }

    @Test
    void reimportSkipsExistingProblems() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("gamma/statement.html", ALPHA_STATEMENT);
        files.put("gamma/tests/sample/a.in", "5");
        files.put("gamma/tests/sample/a.ans", "10");

        ImportPackResponse first = importPack(files, "Week 5");
        assertThat(first.getProblems().get(0).getStatus()).isEqualTo("CREATED");
        long problemsBefore = problemRepository.count();

        ImportPackResponse second = importPack(files, "Week 5");
        assertThat(second.getProblems().get(0).getStatus()).isEqualTo("SKIPPED");
        assertThat(problemRepository.count()).isEqualTo(problemsBefore);
    }

    @Test
    void importManyPairsKeepsOrderAcrossFlushes() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("bulk/statement.html", ALPHA_STATEMENT);
        for (int i = 0; i < 30; i++) {
            String name = String.format("t%02d", i);
            files.put("bulk/tests/secret/" + name + ".in", String.valueOf(i));
            files.put("bulk/tests/secret/" + name + ".ans", String.valueOf(i * 2));
        }

        ImportPackResponse response = importPack(files, "Week 5");

        assertThat(response.getTotalHidden()).isEqualTo(30);
        Problem bulk = problemRepository.findBySlug("bulk").orElseThrow();
        List<TestCase> cases = testCaseRepository
                .findByProblemIdOrderBySortOrderAsc(bulk.getId());
        assertThat(cases).hasSize(30);
        for (int i = 0; i < 30; i++) {
            assertThat(cases.get(i).getSortOrder()).isEqualTo(i);
            assertThat(cases.get(i).getInputData()).isEqualTo(String.valueOf(i));
            assertThat(cases.get(i).getExpectedOutput()).isEqualTo(String.valueOf(i * 2));
        }
    }

    @Test
    void rejectsPackWithoutStatements() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("gamma/tests/sample/a.in", "5");
        files.put("gamma/tests/sample/a.ans", "10");

        assertThatThrownBy(() -> importPack(files, "Week 5"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("statement.html");
    }

    @Test
    void rejectsBlankWeekLabel() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("gamma/statement.html", ALPHA_STATEMENT);

        assertThatThrownBy(() -> importPack(files, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Week label");
    }

    @Test
    void listProblemsCountsHugeRowsWithoutLoadingThem() {
        Problem problem = new Problem();
        problem.setSlug("big-rows");
        problem.setTitle("Big Rows");
        problem.setStatement("Story");
        problem.setInputFormat("-");
        problem.setOutputFormat("-");
        problem.setDifficulty(Difficulty.MEDIUM);
        problem.setWeekLabel("Week 5");
        problem.setTimeLimitMs(2000);
        problem.setMemoryLimitMb(256);
        Problem saved = problemRepository.save(problem);

        TestCase big = new TestCase();
        big.setProblem(saved);
        big.setInputData("x".repeat(5_000_000));
        big.setExpectedOutput("y".repeat(5_000_000));
        big.setSample(false);
        big.setSortOrder(0);
        testCaseRepository.save(big);

        TestCase sample = new TestCase();
        sample.setProblem(saved);
        sample.setInputData("1");
        sample.setExpectedOutput("1");
        sample.setSample(true);
        sample.setSortOrder(1);
        testCaseRepository.save(sample);

        AdminProblemSummary summary = adminService.listProblems().stream()
                .filter(s -> s.getSlug().equals("big-rows"))
                .findFirst()
                .orElseThrow();
        assertThat(summary.getTestCaseCount()).isEqualTo(2);
        assertThat(summary.getSampleCount()).isEqualTo(1);
    }

    private ImportPackResponse importPack(Map<String, String> files, String weekLabel) {
        MultipartFile zip = new MockMultipartFile(
                "file", "pack.zip", "application/zip", zipBytes(files));
        return adminService.importProblemPack(zip, weekLabel, 2000, 256, "MEDIUM");
    }

    private static byte[] zipBytes(Map<String, String> files) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, String> file : files.entrySet()) {
                    zos.putNextEntry(new ZipEntry(file.getKey()));
                    zos.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build test ZIP", e);
        }
    }
}
