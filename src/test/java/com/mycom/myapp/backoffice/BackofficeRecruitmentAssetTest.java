package com.mycom.myapp.backoffice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class BackofficeRecruitmentAssetTest {

    @Test
    void recruitmentViewProvidesSearchListDetailAndVisibilityControls() throws IOException {
        String html =
                new String(
                        new ClassPathResource("static/backoffice/index.html")
                                .getInputStream()
                                .readAllBytes(),
                        StandardCharsets.UTF_8);
        String script =
                new String(
                        new ClassPathResource("static/js/backoffice.js")
                                .getInputStream()
                                .readAllBytes(),
                        StandardCharsets.UTF_8);

        assertThat(html)
                .contains(
                        "boRecruitmentFilterForm",
                        "boRecruitmentListTable",
                        "boRecruitmentDetailPanel");
        assertThat(script)
                .contains(
                        "/api/admin/recruitments",
                        "loadRecruitments",
                        "boRecruitmentVisibilityForm");
    }
}
