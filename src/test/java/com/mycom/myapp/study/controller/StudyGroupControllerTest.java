package com.mycom.myapp.study.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.global.response.ApiResponse;
import com.mycom.myapp.global.security.AuthenticatedMember;
import com.mycom.myapp.member.entity.MemberRole;
import com.mycom.myapp.study.dto.response.MyStudyGroupResponse;
import com.mycom.myapp.study.dto.response.StudyGroupHomeResponse;
import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.entity.GroupStatus;
import com.mycom.myapp.study.service.StudyGroupService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;

class StudyGroupControllerTest {

    private final StudyGroupService studyGroupService = mock(StudyGroupService.class);
    private final StudyGroupController controller = new StudyGroupController(studyGroupService);

    @Test
    void returnsSuccessfulGroupHomeResponseForAuthenticatedMember() {
        StudyGroupHomeResponse home =
                new StudyGroupHomeResponse(
                        10L,
                        25L,
                        "알고리즘 스터디",
                        GroupStatus.ACTIVE,
                        LocalDateTime.of(2026, 7, 1, 10, 0),
                        GroupRole.MEMBER,
                        List.of());
        when(studyGroupService.getHome(10L, 2L)).thenReturn(home);

        ApiResponse<StudyGroupHomeResponse> response =
                controller.getHome(
                        10L, new AuthenticatedMember(2L, "member@example.com", MemberRole.USER));

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo(home);
        assertThat(response.message()).isNull();
        verify(studyGroupService).getHome(10L, 2L);
    }

    @Test
    void rejectsMissingAuthenticatedMember() {
        assertThatThrownBy(() -> controller.getHome(10L, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void returnsMyActiveGroupsForAuthenticatedMember() {
        List<MyStudyGroupResponse> groups =
                List.of(
                        new MyStudyGroupResponse(
                                10L,
                                25L,
                                "알고리즘 스터디",
                                GroupStatus.ACTIVE,
                                GroupRole.LEADER,
                                LocalDateTime.of(2026, 7, 1, 10, 0)));
        when(studyGroupService.getMyGroups(2L)).thenReturn(groups);

        ApiResponse<List<MyStudyGroupResponse>> response =
                controller.getMyGroups(
                        new AuthenticatedMember(2L, "member@example.com", MemberRole.USER));

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo(groups);
        assertThat(response.message()).isNull();
        verify(studyGroupService).getMyGroups(2L);
    }

    @Test
    void rejectsMissingAuthenticatedMemberForMyGroups() {
        assertThatThrownBy(() -> controller.getMyGroups(null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void explicitlyNamesGroupIdPathVariable() throws NoSuchMethodException {
        PathVariable pathVariable =
                StudyGroupController.class
                        .getDeclaredMethod("getHome", Long.class, AuthenticatedMember.class)
                        .getParameters()[0]
                        .getAnnotation(PathVariable.class);

        assertThat(pathVariable.value()).isEqualTo("groupId");
    }
}
