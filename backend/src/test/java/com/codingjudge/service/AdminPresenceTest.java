package com.codingjudge.service;

import com.codingjudge.model.dto.response.PresenceResponse;
import com.codingjudge.model.enums.SubmissionStatus;
import com.codingjudge.repository.ProblemRepository;
import com.codingjudge.repository.SubmissionRepository;
import com.codingjudge.repository.TestCaseRepository;
import com.codingjudge.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPresenceTest {

    @Mock private ProblemRepository problemRepository;
    @Mock private TestCaseRepository testCaseRepository;
    @Mock private UserRepository userRepository;
    @Mock private SubmissionRepository submissionRepository;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
                problemRepository, testCaseRepository, userRepository, submissionRepository);
    }

    @Test
    void presenceMapsRepositoryCounts() {
        when(userRepository.countByLastSeenAtAfter(any())).thenReturn(7L);
        when(userRepository.count()).thenReturn(100L);
        when(submissionRepository.countByStatus(SubmissionStatus.JUDGING)).thenReturn(3L);
        when(submissionRepository.countByStatus(SubmissionStatus.PENDING)).thenReturn(5L);
        when(userRepository.countByCreatedAtAfter(any())).thenReturn(12L);

        PresenceResponse presence = adminService.getPresence();

        assertThat(presence.getActiveUsers()).isEqualTo(7L);
        assertThat(presence.getRegisteredUsers()).isEqualTo(100L);
        assertThat(presence.getJudgingInFlight()).isEqualTo(3L);
        assertThat(presence.getPendingQueue()).isEqualTo(5L);
        assertThat(presence.getSignupsToday()).isEqualTo(12L);
    }
}
