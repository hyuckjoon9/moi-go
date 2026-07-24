package com.mycom.myapp.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mycom.myapp.admin.dto.request.AdminMemberStatusUpdateRequest;
import com.mycom.myapp.admin.dto.response.AdminMemberListResponse;
import com.mycom.myapp.admin.service.AdminMemberService;
import com.mycom.myapp.global.response.ApiResponse;
import com.mycom.myapp.global.security.AuthenticatedMember;
import com.mycom.myapp.member.entity.MemberRole;
import com.mycom.myapp.member.entity.MemberStatus;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

class AdminMemberControllerTest {

    private final AdminMemberService service = mock(AdminMemberService.class);
    private final AdminMemberController controller = new AdminMemberController(service);

    @Test
    void getMembersWrapsServiceResult() {
        AdminMemberListResponse expected = new AdminMemberListResponse(List.of(), 0, 20, 0, 0);
        when(service.getMembers(null, null, null, 0, 20)).thenReturn(expected);

        ApiResponse<AdminMemberListResponse> result =
                controller.getMembers(null, null, null, 0, 20);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isSameAs(expected);
    }

    @Test
    void getMembersDeclaresRequestParameterNames() throws NoSuchMethodException {
        Method method =
                AdminMemberController.class.getMethod(
                        "getMembers",
                        String.class,
                        MemberRole.class,
                        MemberStatus.class,
                        int.class,
                        int.class);

        assertThat(method.getParameters()[0].getAnnotation(RequestParam.class).name())
                .isEqualTo("keyword");
        assertThat(method.getParameters()[1].getAnnotation(RequestParam.class).name())
                .isEqualTo("role");
        assertThat(method.getParameters()[2].getAnnotation(RequestParam.class).name())
                .isEqualTo("status");
        assertThat(method.getParameters()[3].getAnnotation(RequestParam.class).name())
                .isEqualTo("page");
        assertThat(method.getParameters()[4].getAnnotation(RequestParam.class).name())
                .isEqualTo("size");
    }

    @Test
    void memberDetailAndStatusChangeDeclareMemberIdPathVariable() throws NoSuchMethodException {
        Method getMember = AdminMemberController.class.getMethod("getMember", Long.class);
        Method changeStatus =
                AdminMemberController.class.getMethod(
                        "changeStatus",
                        AuthenticatedMember.class,
                        Long.class,
                        AdminMemberStatusUpdateRequest.class);

        assertThat(getMember.getParameters()[0].getAnnotation(PathVariable.class).name())
                .isEqualTo("memberId");
        assertThat(changeStatus.getParameters()[1].getAnnotation(PathVariable.class).name())
                .isEqualTo("memberId");
    }
}
