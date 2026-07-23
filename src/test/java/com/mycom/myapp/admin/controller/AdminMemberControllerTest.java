package com.mycom.myapp.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mycom.myapp.admin.dto.response.AdminMemberListResponse;
import com.mycom.myapp.admin.service.AdminMemberService;
import com.mycom.myapp.global.response.ApiResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

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
}
