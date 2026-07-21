package com.mycom.myapp.recruitment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mycom.myapp.application.service.JoinApplicationService;
import com.mycom.myapp.global.security.AuthenticatedMember;
import com.mycom.myapp.member.entity.MemberRole;
import com.mycom.myapp.recruitment.dto.request.RecruitmentCreateRequest;
import com.mycom.myapp.recruitment.dto.response.RecruitmentResponse;
import com.mycom.myapp.recruitment.service.RecruitmentService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class RecruitmentControllerTest {

    private final RecruitmentService recruitmentService = mock(RecruitmentService.class);
    private final JoinApplicationService joinApplicationService =
            mock(JoinApplicationService.class);
    private final RecruitmentController controller =
            new RecruitmentController(recruitmentService, joinApplicationService);
    private final AuthenticatedMember authenticatedMember =
            new AuthenticatedMember(1L, "leader@test.com", MemberRole.USER);

    @Test
    void createReturnsResponseFromAuthenticatedLeaderId() {
        RecruitmentCreateRequest request =
                new RecruitmentCreateRequest(
                        "제목",
                        "개발",
                        "설명",
                        "목표",
                        "방법",
                        "ONLINE",
                        null,
                        "http://link",
                        "매주 화요일",
                        5,
                        null,
                        "8주",
                        "조건");
        RecruitmentResponse response =
                new RecruitmentResponse(
                        1L,
                        1L,
                        "제목",
                        "개발",
                        "설명",
                        "목표",
                        "방법",
                        "ONLINE",
                        null,
                        "http://link",
                        "매주 화요일",
                        5,
                        null,
                        "8주",
                        "조건",
                        "RECRUITING",
                        null,
                        null);
        when(recruitmentService.create(1L, request)).thenReturn(response);

        var result = controller.create(authenticatedMember, request);

        assertThat(result.data()).isEqualTo(response);
        verify(recruitmentService).create(1L, request);
    }

    @Test
    void getListReturnsPagedResult() {
        var page = new PageImpl<RecruitmentResponse>(java.util.List.of(), PageRequest.of(0, 10), 0);
        when(recruitmentService.getList(null, PageRequest.of(0, 10))).thenReturn(page);

        var result = controller.getList(null, PageRequest.of(0, 10));

        assertThat(result.data()).isEqualTo(page);
    }

    @Test
    void getDetailReturnsResponse() {
        RecruitmentResponse response =
                new RecruitmentResponse(
                        1L,
                        1L,
                        "제목",
                        "개발",
                        "설명",
                        "목표",
                        "방법",
                        "ONLINE",
                        null,
                        "http://link",
                        "매주 화요일",
                        5,
                        null,
                        "8주",
                        "조건",
                        "RECRUITING",
                        null,
                        null);
        when(recruitmentService.getDetail(1L)).thenReturn(response);

        var result = controller.getDetail(1L);

        assertThat(result.data()).isEqualTo(response);
    }
}
