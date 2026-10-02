package com.codingjudge.service;

import com.codingjudge.exception.ForbiddenException;
import com.codingjudge.exception.PayloadTooLargeException;
import com.codingjudge.exception.ResourceNotFoundException;
import com.codingjudge.judge.JudgeEngine;
import com.codingjudge.model.dto.request.SubmitRequest;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final SubmissionTestResultRepository testResultRepository;
    private final UserRepository userRepository;
    private final ProblemRepository problemRepository;
    private final JudgeEngine judgeEngine;
    private final int maxSourceSizeKb;

    public SubmissionService(SubmissionRepository submissionRepository,
                             SubmissionTestResultRepository testResultRepository,
                             UserRepository userRepository,
                             ProblemRepository problemRepository,
                             JudgeEngine judgeEngine,
                             @Value("${judge.max-source-size-kb:256}") int maxSourceSizeKb) {
        this.submissionRepository = submissionRepository;
        this.testResultRepository = testResultRepository;
        this.userRepository = userRepository;
        this.problemRepository = problemRepository;
        this.judgeEngine = judgeEngine;
        this.maxSourceSizeKb = maxSourceSizeKb;
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
        
        // Judge the submission
        submission = judgeEngine.judge(submission);
        submissionRepository.save(submission);

        return SubmissionRefResponse.from(submission);
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
        return SubmissionDetailResponse.from(submission, results);
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
