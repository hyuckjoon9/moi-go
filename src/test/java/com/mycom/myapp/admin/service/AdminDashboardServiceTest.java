package com.mycom.myapp.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mycom.myapp.admin.dto.response.AdminDashboardResponse;
import com.mycom.myapp.admin.dto.response.AdminDashboardResponse.GroupCounts;
import com.mycom.myapp.admin.dto.response.AdminDashboardResponse.MemberCounts;
import com.mycom.myapp.admin.dto.response.AdminDashboardResponse.RecruitmentCounts;
import com.mycom.myapp.admin.repository.AdminDashboardQueryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminDashboardServiceTest {

    private final AdminDashboardQueryRepository repository =
            mock(AdminDashboardQueryRepository.class);
    private final AdminDashboardService service = new AdminDashboardService(repository);

    @Test
    void getDashboardReturnsReadModelResult() {
        AdminDashboardResponse expected =
                new AdminDashboardResponse(
                        new MemberCounts(2, 1, 0, 1),
                        new RecruitmentCounts(1, 0, 0, 0),
                        new GroupCounts(1, 0),
                        List.of());
        when(repository.findDashboard()).thenReturn(expected);

        assertThat(service.getDashboard()).isSameAs(expected);
        verify(repository).findDashboard();
    }
}
