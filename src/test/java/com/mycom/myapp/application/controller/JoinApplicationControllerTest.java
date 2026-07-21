package com.mycom.myapp.application.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mycom.myapp.application.dto.request.JoinApplicationCreateRequest;
import com.mycom.myapp.application.dto.response.JoinApplicationResponse;
import com.mycom.myapp.application.entity.ApplicationStatus;
import com.mycom.myapp.application.service.JoinApplicationService;
import com.mycom.myapp.global.security.AuthenticatedMember;
import com.mycom.myapp.member.entity.MemberRole;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class JoinApplicationControllerTest {

    private final JoinApplicationService joinApplicationService =
            mock(JoinApplicationService.class);
    private final JoinApplicationController controller =
            new JoinApplicationController(joinApplicationService);
    private final AuthenticatedMember authenticatedMember =
            new AuthenticatedMember(2L, "applicant@test.com", MemberRole.USER);

    @Test
    void createReturnsResponseFromAuthenticatedApplicantId() {
        JoinApplicationCreateRequest request =
                new JoinApplicationCreateRequest("지원동기", "경험", "주말 가능", "백엔드");
        JoinApplicationResponse response =
                new JoinApplicationResponse(
                        1L,
                        10L,
                        2L,
                        "지원자닉네임",
                        "지원동기",
                        "경험",
                        "주말 가능",
                        "백엔드",
                        ApplicationStatus.PENDING,
                        LocalDateTime.now());
        when(joinApplicationService.create(10L, 2L, request)).thenReturn(response);

        var result = controller.create(authenticatedMember, 10L, request);

        assertThat(result.data()).isEqualTo(response);
        verify(joinApplicationService).create(10L, 2L, request);
    }
}
