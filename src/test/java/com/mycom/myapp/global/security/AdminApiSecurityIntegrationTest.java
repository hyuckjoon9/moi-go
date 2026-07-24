package com.mycom.myapp.global.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AdminApiSecurityIntegrationTest.AdminTestEndpoint.class)
class AdminApiSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void anonymousRequestReturnsUnauthorizedJson() throws Exception {
        mockMvc.perform(get("/api/admin/test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));
    }

    @Test
    void userRequestReturnsForbiddenJson() throws Exception {
        mockMvc.perform(get("/api/admin/test").with(user("user").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("관리자 권한이 필요합니다."));
    }

    @Test
    void adminRequestIsAllowed() throws Exception {
        mockMvc.perform(get("/api/admin/test").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @RestController
    static class AdminTestEndpoint {

        @GetMapping("/api/admin/test")
        String get() {
            return "ok";
        }
    }
}
