package com.codingjudge.repository;

import com.codingjudge.model.entity.Problem;
import com.codingjudge.model.entity.TestCase;
import com.codingjudge.model.enums.Difficulty;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ProblemRepositoryTest {

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private TestCaseRepository testCaseRepository;

    private Problem newProblem(String slug, String title, Difficulty difficulty, String week) {
        Problem problem = new Problem();
        problem.setSlug(slug);
        problem.setTitle(title);
        problem.setStatement("Statement for " + title);
        problem.setInputFormat("Input format");
        problem.setOutputFormat("Output format");
        problem.setConstraints("1 <= n <= 100");
        problem.setDifficulty(difficulty);
        problem.setWeekLabel(week);
        return problem;
    }

    private TestCase newTestCase(Problem problem, String input, String output,
                                 boolean sample, int order) {
        TestCase testCase = new TestCase();
        testCase.setProblem(problem);
        testCase.setInputData(input);
        testCase.setExpectedOutput(output);
        testCase.setSample(sample);
        testCase.setSortOrder(order);
        return testCase;
    }

    @Test
    void saveAndFindBySlug() {
        problemRepository.save(newProblem("power-cut", "Power Cut", Difficulty.EASY, "Week 1"));

        assertThat(problemRepository.findBySlug("power-cut")).isPresent();
        assertThat(problemRepository.findBySlug("missing")).isEmpty();
    }

    @Test
    void filterByWeekAndDifficulty() {
        problemRepository.save(newProblem("p1", "Alpha", Difficulty.EASY, "Week 1"));
        problemRepository.save(newProblem("p2", "Beta", Difficulty.MEDIUM, "Week 1"));
        problemRepository.save(newProblem("p3", "Gamma", Difficulty.EASY, "Week 2"));

        assertThat(problemRepository.findByWeekLabel("Week 1")).hasSize(2);
        assertThat(problemRepository.findByDifficulty(Difficulty.EASY)).hasSize(2);
    }

    @Test
    void searchByTitle() {
        problemRepository.save(newProblem("lb", "Hackathon Leaderboard", Difficulty.MEDIUM, "Week 1"));

        List<Problem> found = problemRepository.findByTitleContainingIgnoreCase("leaderboard");

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getSlug()).isEqualTo("lb");
    }

    @Test
    void duplicateSlugRejected() {
        problemRepository.save(newProblem("dup", "First", Difficulty.EASY, "Week 1"));

        assertThatThrownBy(() -> {
            problemRepository.saveAndFlush(newProblem("dup", "Second", Difficulty.EASY, "Week 1"));
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void testCasesPersistedWithProblem() {
        Problem problem = newProblem("tc-prob", "TC Problem", Difficulty.EASY, "Week 1");
        problem.addTestCase(newTestCase(problem, "2\n3", "6", true, 0));
        problem.addTestCase(newTestCase(problem, "5", "25", false, 1));
        problemRepository.save(problem);

        List<TestCase> all = testCaseRepository.findByProblemIdOrderBySortOrderAsc(problem.getId());
        assertThat(all).hasSize(2);
        assertThat(all.get(0).getSample()).isTrue();

        List<TestCase> samples =
                testCaseRepository.findByProblemIdAndSampleTrueOrderBySortOrderAsc(problem.getId());
        assertThat(samples).hasSize(1);
        assertThat(samples.get(0).getInputData()).isEqualTo("2\n3");
    }

    @Test
    void deletingProblemCascadesToTestCases() {
        Problem problem = newProblem("cascade", "Cascade", Difficulty.EASY, "Week 1");
        problem.addTestCase(newTestCase(problem, "1", "1", true, 0));
        problemRepository.save(problem);
        Long problemId = problem.getId();

        problemRepository.deleteById(problemId);

        assertThat(testCaseRepository.findByProblemIdOrderBySortOrderAsc(problemId)).isEmpty();
    }
}
