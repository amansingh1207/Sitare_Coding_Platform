package com.codingjudge.service;

import com.codingjudge.exception.ForbiddenException;
import com.codingjudge.exception.PayloadTooLargeException;
import com.codingjudge.exception.ResourceNotFoundException;
import com.codingjudge.judge.ExecutionResult;
import com.codingjudge.judge.JudgeEngine;
import com.codingjudge.model.dto.request.CustomRunRequest;
import com.codingjudge.model.dto.request.SubmitRequest;
import com.codingjudge.model.dto.response.CustomRunResponse;
import com.codingjudge.model.dto.response.RunResultResponse;
import com.codingjudge.model.dto.response.SubmissionDetailResponse;
import com.codingjudge.model.dto.response.SubmissionRefResponse;
import com.codingjudge.model.dto.response.SubmissionSummaryResponse;
import com.codingjudge.model.dto.response.SubmissionTestResultResponse;
import com.codingjudge.model.entity.Problem;
import com.codingjudge.model.entity.Submission;
import com.codingjudge.model.entity.User;
import com.codingjudge.model.enums.Language;
import com.codingjudge.model.enums.SubmissionStatus;
import com.codingjudge.repository.ProblemRepository;
import com.codingjudge.repository.SubmissionRepository;
import com.codingjudge.repository.SubmissionTestResultRepository;
import com.codingjudge.repository.UserRepository;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Service
public class SubmissionService {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionService.class);

    private final SubmissionRepository submissionRepository;
    private final SubmissionTestResultRepository testResultRepository;
    private final UserRepository userRepository;
    private final ProblemRepository problemRepository;
    private final JudgeEngine judgeEngine;
    private final int maxSourceSizeKb;
    private final boolean workerEnabled;
    /**
     * Programmatic transactions for the judging worker: each phase gets its
     * own short transaction (field injection avoids the self-invocation trap
     * of @Transactional methods calling each other on the same bean).
     */
    private final TransactionTemplate txTemplate;

    public SubmissionService(SubmissionRepository submissionRepository,
                             SubmissionTestResultRepository testResultRepository,
                             UserRepository userRepository,
                             ProblemRepository problemRepository,
                             JudgeEngine judgeEngine,
                             PlatformTransactionManager transactionManager,
                             @Value("${judge.max-source-size-kb:256}") int maxSourceSizeKb,
                             @Value("${judge.worker.enabled:true}") boolean workerEnabled) {
        this.submissionRepository = submissionRepository;
        this.testResultRepository = testResultRepository;
        this.userRepository = userRepository;
        this.problemRepository = problemRepository;
        this.judgeEngine = judgeEngine;
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.maxSourceSizeKb = maxSourceSizeKb;
        this.workerEnabled = workerEnabled;
    }

    @Transactional
    public SubmissionRefResponse submit(String email, SubmitRequest request) {
        User user = requireUser(email);
        Problem problem = problemRepository.findById(request.getProblemId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Problem not found: " + request.getProblemId()));
        Language language = parseLanguage(request.getLanguage());
        requireSourceSize(request.getSourceCode());

        Submission submission = new Submission();
        submission.setUser(user);
        submission.setProblem(problem);
        submission.setLanguage(language);
        submission.setSourceCode(request.getSourceCode());
        submission.setStatus(SubmissionStatus.PENDING);

        submission = submissionRepository.save(submission);

        if (!workerEnabled) {
            // Synchronous legacy path (tests + kill-switch): judge inline,
            // exactly as before the queue existed.
            submission = judgeEngine.judge(submission);
            submissionRepository.save(submission);
            return SubmissionRefResponse.from(submission);
        }

        // Queued path: the background worker claims PENDING rows, judges
        // them and persists terminal verdicts. The 202 response carries the
        // id; clients poll GET /api/submissions/{id} (unchanged contract).
        return SubmissionRefResponse.from(submission);
    }

    /**
     * Judge one queued submission on a worker thread.
     *
     * <p>Deliberately NOT one big transaction: judging waits on the provider
     * (up to ~120s on DOMjudge, up to ~90s on hosted custom-run backends)
     * and must never hold a pool connection while doing so — that starved
     * the pool (total=10, active=10, waiting=21) and broke even login under
     * burst load. Instead each phase below runs in its own short
     * transaction: atomic claim, detached load, judge with no connection
     * held, then persist. The atomic claim still guarantees exactly-once
     * processing, and startup recovery still repairs rows stranded in
     * JUDGING by a JVM death.
     */
    public void judgeQueued(Long submissionId) {
        Boolean claimed = txTemplate.execute(status -> submissionRepository.claimQueued(
                submissionId, SubmissionStatus.PENDING, SubmissionStatus.JUDGING) == 1);
        if (!Boolean.TRUE.equals(claimed)) {
            return;
        }
        try {
            Submission detached = txTemplate.execute(status -> {
                Submission submission = submissionRepository.findById(submissionId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Claimed submission vanished: " + submissionId));
                Hibernate.initialize(submission.getProblem());
                Hibernate.initialize(submission.getProblem().getTestCases());
                return submission;
            });
            Submission judged = judgeEngine.judge(detached);
            txTemplate.execute(status -> {
                submissionRepository.save(judged);
                return null;
            });
        } catch (Throwable t) {
            LOG.error("Queued judging failed for submission {}, marking INTERNAL_ERROR",
                    submissionId, t);
            markInternalError(submissionId);
        }
    }

    private void markInternalError(Long submissionId) {
        try {
            txTemplate.execute(status -> {
                Submission failed = submissionRepository.findById(submissionId)
                        .orElse(null);
                if (failed != null) {
                    failed.setStatus(SubmissionStatus.INTERNAL_ERROR);
                    failed.setJudgedAt(Instant.now());
                    submissionRepository.save(failed);
                }
                return null;
            });
        } catch (RuntimeException inner) {
            LOG.error("Could not persist INTERNAL_ERROR for submission {}",
                    submissionId, inner);
        }
    }

    /**
     * Runs code against a problem's sample test cases without recording a submission.
     *
     * Nothing is persisted, so this cannot pollute submission history, and only
     * sample test cases are executed, so hidden ones stay unreachable.
     *
     * <p>Deliberately NOT transactional: judging waits on the provider and must
     * never hold a pool connection while doing so. Test cases are loaded
     * eagerly up front, so no lazy access happens during the wait.
     */
    public RunResultResponse run(String email, SubmitRequest request) {
        requireUser(email);
        Problem problem = problemRepository.findWithTestCasesById(request.getProblemId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Problem not found: " + request.getProblemId()));
        Language language = parseLanguage(request.getLanguage());
        requireSourceSize(request.getSourceCode());

        return RunResultResponse.from(
                judgeEngine.runSamples(problem, request.getSourceCode(), language));
    }

    /**
     * Runs code against a caller-supplied stdin without recording a submission.
     *
     * Nothing is persisted and no test case data is involved, so there is
     * nothing hidden to leak; any authenticated user may use it.
     *
     * <p>Deliberately NOT transactional: the hosted custom-run call waits on
     * the network and must never hold a pool connection while doing so.
     */
    public CustomRunResponse runCustom(String email, CustomRunRequest request) {
        requireUser(email);
        Problem problem = problemRepository.findById(request.getProblemId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Problem not found: " + request.getProblemId()));
        Language language = parseLanguage(request.getLanguage());
        requireSourceSize(request.getSourceCode());

        ExecutionResult result = judgeEngine.runCustomInput(
                problem, request.getSourceCode(), language,
                request.getStdin() == null ? "" : request.getStdin());
        return CustomRunResponse.from(result, judgeEngine.executionStatus(result));
    }

    @Transactional(readOnly = true)
    public SubmissionDetailResponse getForUser(String email, Long id) {
        User user = requireUser(email);
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found: " + id));
        requireOwnership(user, submission);

        // Read results through the repository rather than the Submission's
        // in-memory collection: the judge saves results directly, which leaves
        // that collection stale within the same persistence context.
        List<SubmissionTestResultResponse> results = testResultRepository
                .findBySubmissionId(submission.getId())
                .stream()
                .filter(result -> Boolean.TRUE.equals(result.getTestCase().getSample()))
                .map(SubmissionTestResultResponse::from)
                .toList();
        return SubmissionDetailResponse.from(submission, results,
                queuePositionOf(submission));
    }

    /**
     * 1-based place in the judging queue, shown while the submission waits.
     * Counts PENDING rows submitted earlier (the worker judges oldest
     * first); null once judging starts or finishes, so callers show the
     * verdict instead of a stale number.
     */
    private Integer queuePositionOf(Submission submission) {
        if (submission.getStatus() != SubmissionStatus.PENDING) {
            return null;
        }
        long ahead = submissionRepository.countByStatusAndSubmittedAtBefore(
                SubmissionStatus.PENDING, submission.getSubmittedAt());
        return (int) Math.min(ahead + 1, Integer.MAX_VALUE);
    }

    /**
     * IDs of problems the user has solved. Backs the problem list's solved
     * checkmarks and the progress dashboard; a single indexed query.
     */
    @Transactional(readOnly = true)
    public List<Long> solvedProblemIds(String email) {
        User user = requireUser(email);
        return submissionRepository.findSolvedProblemIds(user.getId(), SubmissionStatus.ACCEPTED);
    }

    @Transactional(readOnly = true)
    public Page<SubmissionSummaryResponse> listForUser(String email, Long problemId,
                                                       String status, String language,
                                                       Pageable pageable) {
        User user = requireUser(email);
        SubmissionStatus statusEnum = parseStatus(status);
        Language languageEnum = parseLanguageOrNull(language);
        return submissionRepository
                .searchForUser(user.getId(), problemId, statusEnum, languageEnum, pageable)
                .map(SubmissionSummaryResponse::from);
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }

    private void requireOwnership(User user, Submission submission) {
        if (!submission.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("You do not have access to this submission");
        }
    }

    private void requireSourceSize(String sourceCode) {
        if (sourceCode != null && sourceCode.length() > (long) maxSourceSizeKb * 1024) {
            throw new PayloadTooLargeException(
                    "Source code exceeds the " + maxSourceSizeKb + " KB limit");
        }
    }

    private Language parseLanguage(String language) {
        try {
            return Language.valueOf(language);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(
                    "Invalid language: " + language + ". Allowed values: JAVA, CPP, PYTHON");
        }
    }

    private Language parseLanguageOrNull(String language) {
        if (!StringUtils.hasText(language)) {
            return null;
        }
        return parseLanguage(language.trim().toUpperCase());
    }

    private SubmissionStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return SubmissionStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid status: " + status + ". Allowed values: PENDING, JUDGING, ACCEPTED, "
                            + "WRONG_ANSWER, COMPILATION_ERROR, RUNTIME_ERROR, "
                            + "TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED, INTERNAL_ERROR");
        }
    }
}
