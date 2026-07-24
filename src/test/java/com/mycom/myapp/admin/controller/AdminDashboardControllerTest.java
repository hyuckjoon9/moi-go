package com.mycom.myapp.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mycom.myapp.admin.dto.response.AdminDashboardResponse;
import com.mycom.myapp.admin.dto.response.AdminDashboardResponse.GroupCounts;
import com.mycom.myapp.admin.dto.response.AdminDashboardResponse.MemberCounts;
import com.mycom.myapp.admin.dto.response.AdminDashboardResponse.RecruitmentCounts;
import com.mycom.myapp.admin.service.AdminDashboardService;
import com.mycom.myapp.global.response.ApiResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminDashboardControllerTest {

    private final AdminDashboardService service = mock(AdminDashboardService.class);
    private final AdminDashboardController controller = new AdminDashboardController(service);

    @Test
    void getDashboardWrapsServiceResult() {
        AdminDashboardResponse expected =
                new AdminDashboardResponse(
                        new MemberCounts(0, 0, 0, 0),
                        new RecruitmentCounts(0, 0, 0, 0),
                        new GroupCounts(0, 0),
                        List.of());
        when(service.getDashboard()).thenReturn(expected);

        ApiResponse<AdminDashboardResponse> result = controller.getDashboard();

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isSameAs(expected);
    }
}
