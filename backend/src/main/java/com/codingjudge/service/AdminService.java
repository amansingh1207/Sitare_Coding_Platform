package com.codingjudge.service;

import com.codingjudge.model.dto.response.AdminProblemSummary;
import com.codingjudge.model.dto.response.ImportPackResponse;
import com.codingjudge.model.dto.response.ImportedProblemSummary;
import com.codingjudge.model.entity.Problem;
import com.codingjudge.model.entity.TestCase;
import com.codingjudge.model.enums.Difficulty;
import com.codingjudge.repository.ProblemRepository;
import com.codingjudge.repository.TestCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class AdminService {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;

    public AdminService(ProblemRepository problemRepository,
                        TestCaseRepository testCaseRepository) {
        this.problemRepository = problemRepository;
        this.testCaseRepository = testCaseRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminProblemSummary> listProblems() {
        List<Problem> problems = problemRepository.findAll();
        List<AdminProblemSummary> result = new ArrayList<>();
        for (Problem problem : problems) {
            int total = testCaseRepository.findByProblemIdOrderBySortOrderAsc(problem.getId()).size();
            int samples = testCaseRepository
                    .findByProblemIdAndSampleTrueOrderBySortOrderAsc(problem.getId()).size();
            result.add(AdminProblemSummary.from(problem, total, samples));
        }
        result.sort(Comparator.comparing(AdminProblemSummary::getId));
        return result;
    }

    /**
     * Imports a whole contest pack ZIP shared by a professor.
     *
     * Expected layout (one folder per problem, nesting root is ignored):
     * <pre>
     * pack-root/
     *   A-notes-chain/
     *     statement.html
     *     tests/sample/*.in + *.ans
     *     tests/secret/*.in + *.ans
     *   B-power-cut/
     *     ...
     * </pre>
     * Problems whose slug already exists are skipped and reported.
     */
    @Transactional
    public ImportPackResponse importProblemPack(MultipartFile file, String weekLabel,
                                                Integer defaultTimeLimitMs, Integer defaultMemoryLimitMb,
                                                String defaultDifficulty) {
        if (!StringUtils.hasText(weekLabel)) {
            throw new IllegalArgumentException("Week label is required");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("Only ZIP files are supported");
        }

        int timeLimitMs = defaultTimeLimitMs != null ? defaultTimeLimitMs : 2000;
        int memoryLimitMb = defaultMemoryLimitMb != null ? defaultMemoryLimitMb : 256;
        Difficulty fallbackDifficulty = parseDifficultyLenient(defaultDifficulty);

        Map<String, String> entries = readZipEntries(file);
        Map<String, String> statements = new TreeMap<>();
        for (String name : entries.keySet()) {
            if (name.toLowerCase().endsWith("/statement.html")
                    || name.equalsIgnoreCase("statement.html")) {
                int slash = name.lastIndexOf('/');
                String folder = slash >= 0 ? name.substring(0, slash) : "";
                statements.put(folder, entries.get(name));
            }
        }
        if (statements.isEmpty()) {
            throw new IllegalArgumentException(
                    "No problem folders found: expected <problem>/statement.html inside the ZIP");
        }

        List<ImportedProblemSummary> results = new ArrayList<>();
        for (Map.Entry<String, String> folderEntry : statements.entrySet()) {
            String folder = folderEntry.getKey();
            String baseName = folder.contains("/") ? folder.substring(folder.lastIndexOf('/') + 1) : folder;
            String slug = sanitizeSlug(baseName);

            if (problemRepository.existsBySlug(slug)) {
                results.add(new ImportedProblemSummary(slug, baseName, "SKIPPED", 0, 0,
                        "Problem already exists"));
                continue;
            }

            ParsedStatement parsed = parseStatementHtml(folderEntry.getValue());
            String title = StringUtils.hasText(parsed.title) ? parsed.title : baseName;

            Problem problem = new Problem();
            problem.setSlug(slug);
            problem.setTitle(title);
            problem.setStatement(StringUtils.hasText(parsed.story) ? parsed.story : title);
            problem.setInputFormat(StringUtils.hasText(parsed.inputFormat) ? parsed.inputFormat : "-");
            problem.setOutputFormat(StringUtils.hasText(parsed.outputFormat) ? parsed.outputFormat : "-");
            problem.setConstraints(null);
            problem.setDifficulty(parsed.difficulty != null ? parsed.difficulty : fallbackDifficulty);
            problem.setWeekLabel(weekLabel.trim());
            problem.setTimeLimitMs(parsed.timeLimitMs != null ? parsed.timeLimitMs : timeLimitMs);
            problem.setMemoryLimitMb(memoryLimitMb);
            Problem saved = problemRepository.save(problem);

            Map<String, String> sampleFiles = collectTestFiles(entries, folder, "sample");
            Map<String, String> secretFiles = collectTestFiles(entries, folder, "secret");

            int order = 0;
            int sampleCount = 0;
            for (Map.Entry<String, String> test : sampleFiles.entrySet()) {
                TestCase tc = buildTestCase(saved, test.getKey(), test.getValue(), true, order++);
                testCaseRepository.save(tc);
                sampleCount++;
            }
            int hiddenCount = 0;
            for (Map.Entry<String, String> test : secretFiles.entrySet()) {
                TestCase tc = buildTestCase(saved, test.getKey(), test.getValue(), false, order++);
                testCaseRepository.save(tc);
                hiddenCount++;
            }

            results.add(new ImportedProblemSummary(slug, title, "CREATED",
                    sampleCount, hiddenCount,
                    sampleCount + hiddenCount == 0 ? "No .in/.ans pairs found" : "Imported successfully"));
        }

        return new ImportPackResponse(results);
    }

    private Map<String, String> readZipEntries(MultipartFile file) {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), readAllText(zis));
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read ZIP file: " + e.getMessage());
        }
        return entries;
    }

    private String readAllText(ZipInputStream zis) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
        String line;
        boolean first = true;
        while ((line = reader.readLine()) != null) {
            if (!first) {
                sb.append("\n");
            }
            sb.append(line);
            first = false;
        }
        return sb.toString();
    }

    private Map<String, String> collectTestFiles(Map<String, String> entries, String folder, String kind) {
        String prefix = folder.isEmpty() ? "" : folder + "/";
        Map<String, String> inputs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, String> outputs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, String> e : entries.entrySet()) {
            String name = e.getKey();
            if (!name.startsWith(prefix)) {
                continue;
            }
            String relative = name.substring(prefix.length());
            if (!relative.startsWith("tests/" + kind + "/")) {
                continue;
            }
            String fileName = relative.substring(("tests/" + kind + "/").length());
            if (fileName.contains("/")) {
                continue;
            }
            String lower = fileName.toLowerCase();
            if (lower.endsWith(".in")) {
                inputs.put(fileName.substring(0, fileName.length() - 3), e.getValue());
            } else if (lower.endsWith(".ans")) {
                outputs.put(fileName.substring(0, fileName.length() - 4), e.getValue());
            }
        }
        Map<String, String> pairs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, String> in : inputs.entrySet()) {
            String ans = findIgnoreCase(outputs, in.getKey());
            if (ans != null) {
                pairs.put(in.getKey(), in.getValue() + "\n---ANS---\n" + ans);
            }
        }
        return pairs;
    }

    private String findIgnoreCase(Map<String, String> map, String key) {
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    private TestCase buildTestCase(Problem problem, String name, String packed,
                                   boolean sample, int order) {
        int sep = packed.indexOf("\n---ANS---\n");
        TestCase tc = new TestCase();
        tc.setProblem(problem);
        tc.setInputData(packed.substring(0, sep));
        tc.setExpectedOutput(packed.substring(sep + "\n---ANS---\n".length()));
        tc.setSample(sample);
        tc.setSortOrder(order);
        return tc;
    }

    private String sanitizeSlug(String raw) {
        String slug = raw == null ? "" : raw.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        slug = slug.replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            slug = "problem";
        }
        if (slug.length() > 100) {
            slug = slug.substring(0, 100).replaceAll("-+$", "");
        }
        return slug;
    }

    private Difficulty parseDifficultyLenient(String difficulty) {
        if (!StringUtils.hasText(difficulty)) {
            return Difficulty.MEDIUM;
        }
        try {
            return Difficulty.valueOf(difficulty.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Difficulty.MEDIUM;
        }
    }

    private static class ParsedStatement {
        String title;
        String story;
        String inputFormat;
        String outputFormat;
        Difficulty difficulty;
        Integer timeLimitMs;
    }

    private ParsedStatement parseStatementHtml(String html) {
        ParsedStatement parsed = new ParsedStatement();
        if (html == null) {
            return parsed;
        }
        parsed.title = extractTag(html, "h1");
        String meta = extractFirst(html, "<p\\s+class=\"meta\"[^>]*>(.*?)</p>");
        if (meta != null) {
            String metaText = stripTags(meta).toLowerCase();
            Matcher diff = Pattern.compile("difficulty:\\s*([^.]+)").matcher(metaText);
            if (diff.find()) {
                String level = diff.group(1).trim();
                if (level.contains("very easy") || level.equals("easy")) {
                    parsed.difficulty = Difficulty.EASY;
                } else if (level.contains("medium")) {
                    parsed.difficulty = Difficulty.MEDIUM;
                } else if (level.contains("hard")) {
                    parsed.difficulty = Difficulty.HARD;
                }
            }
            Matcher time = Pattern.compile("time limit:\\s*([\\d.]+)\\s*seconds?").matcher(metaText);
            if (time.find()) {
                try {
                    parsed.timeLimitMs = (int) (Double.parseDouble(time.group(1)) * 1000);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        String headerEnd = "</header>";
        int bodyStart = html.toLowerCase().indexOf(headerEnd);
        String afterHeader = bodyStart >= 0 ? html.substring(bodyStart + headerEnd.length()) : html;
        String inputHeading = findHeading(afterHeader, "input");
        String outputHeading = findHeading(afterHeader, "output");
        String examplesHeading = findHeading(afterHeader, "examples");
        if (inputHeading != null) {
            parsed.story = paragraphsToText(afterHeader.substring(0, afterHeader.indexOf(inputHeading)));
            if (outputHeading != null) {
                parsed.inputFormat = paragraphsToText(
                        afterHeader.substring(afterHeader.indexOf(inputHeading) + inputHeading.length(),
                                afterHeader.indexOf(outputHeading)));
                if (examplesHeading != null) {
                    parsed.outputFormat = paragraphsToText(
                            afterHeader.substring(afterHeader.indexOf(outputHeading) + outputHeading.length(),
                                    afterHeader.indexOf(examplesHeading)));
                } else {
                    parsed.outputFormat = paragraphsToText(
                            afterHeader.substring(afterHeader.indexOf(outputHeading) + outputHeading.length()));
                }
            }
        } else {
            parsed.story = paragraphsToText(afterHeader);
        }
        return parsed;
    }

    private String findHeading(String html, String name) {
        Matcher m = Pattern.compile("<h2[^>]*>\\s*" + name + "\\s*</h2>",
                Pattern.CASE_INSENSITIVE).matcher(html);
        return m.find() ? m.group(0) : null;
    }

    private String extractTag(String html, String tag) {
        Matcher m = Pattern.compile("<" + tag + "[^>]*>(.*?)</" + tag + ">",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        return m.find() ? stripTags(m.group(1)).trim() : null;
    }

    private String extractFirst(String html, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        return m.find() ? m.group(1) : null;
    }

    private String paragraphsToText(String htmlFragment) {
        if (htmlFragment == null) {
            return "";
        }
        String withBreaks = htmlFragment.replaceAll("(?i)</p\\s*>", "\n\n")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</li\\s*>", "\n");
        String text = stripTags(withBreaks);
        text = text.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        text = text.replaceAll("\\n ", "\n").replaceAll(" \\n", "\n");
        text = text.replaceAll("\\n{3,}", "\n\n").trim();
        return text;
    }

    private String stripTags(String html) {
        if (html == null) {
            return "";
        }
        String text = html.replaceAll("<[^>]*>", "");
        return unescapeEntities(text);
    }

    private String unescapeEntities(String text) {
        return text.replace("&le;", "\u2264")
                .replace("&ge;", "\u2265")
                .replace("&ne;", "\u2260")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ");
    }
}
