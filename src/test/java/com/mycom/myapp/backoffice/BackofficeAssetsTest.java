package com.mycom.myapp.backoffice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BackofficeAssetsTest {

    private static final Path BACKOFFICE_HTML =
            Path.of("src/main/resources/static/backoffice/index.html");
    private static final Path BACKOFFICE_SCRIPT =
            Path.of("src/main/resources/static/js/backoffice.js");
    private static final Path BACKOFFICE_STYLE =
            Path.of("src/main/resources/static/css/backoffice.css");

    @Test
    void backofficeUsesReadableAuditHistoryAndOpaqueNotificationSurface() throws IOException {
        String html = Files.readString(BACKOFFICE_HTML);
        String script = Files.readString(BACKOFFICE_SCRIPT);
        String style = Files.readString(BACKOFFICE_STYLE);

        assertThat(html).doesNotContain("<img class=\"brand-logo\"");
        assertThat(html)
                .contains("/css/backoffice.css?v=20260724-operations-fix")
                .contains("id=\"view-operations\"")
                .contains("id=\"boAuditFilterForm\"")
                .contains("class=\"bo-table-container bo-operation-table\"");
        assertThat(html)
                .contains(
                        "<h3 class=\"bo-panel-title\"><span class=\"bo-panel-icon\">○</span> 회원 상태 구성 비율</h3>")
                .contains(
                        "<h3 class=\"bo-panel-title\"><span class=\"bo-panel-icon\">▥</span> 모집 및 스터디 그룹 현황</h3>")
                .contains(
                        "<h3 class=\"bo-panel-title\"><span class=\"bo-panel-icon\">◇</span> 최근 운영 조치 내역 (Audit Trail)</h3>");
        assertThat(script)
                .contains(
                        "${formatAuditAction(a.action)} · ${formatDate(a.createdAt)} · ${a.reason}");
        assertThat(script)
                .contains("/api/admin/groups")
                .contains("/api/admin/attendance-records")
                .contains("/api/admin/activity-records")
                .contains("/api/admin/audit-logs")
                .contains("운영 조회 API를 불러오지 못했습니다");
        assertThat(style)
                .contains("--bo-layout-space: clamp(1rem, 2vw, 2.5rem);")
                .contains("--bo-content-space: clamp(1.25rem, 2.5vw, 2.5rem);")
                .contains("background: var(--bo-dropdown-bg);")
                .contains("--bs-table-bg: var(--bo-table-bg);")
                .contains("--bs-table-color: var(--bo-text-secondary);")
                .contains("margin-left: calc(var(--bo-sidebar-width) + var(--bo-layout-space));")
                .contains("padding: var(--bo-content-space);")
                .contains("gap: var(--bo-content-space);");
        assertThat(style).contains(".bo-operation-table");
    }
}
