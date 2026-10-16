package com.tgnguyen.layoutlybe.controller;

import com.tgnguyen.layoutlybe.config.CorsConfig;
import com.tgnguyen.layoutlybe.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
@Import(CorsConfig.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
class AccountCorsTest {
    @Autowired MockMvc mvc;
    @MockBean AccountService accounts;
    @MockBean com.tgnguyen.layoutlybe.service.PasswordResetService reset;
    @MockBean com.tgnguyen.layoutlybe.service.SavedProjectService projects;

    @Test void permitsProfileAndDeletePreflightsFromTheFrontendProxy() throws Exception {
        for (String method : new String[]{"PATCH", "DELETE"}) {
            mvc.perform(options("/api/auth/profile").header("Origin", "http://127.0.0.1:5174")
                    .header("Access-Control-Request-Method", method)
                    .header("Access-Control-Request-Headers", "content-type,x-csrf-token"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5174"))
                    .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        }
    }

    @Test void rejectsUntrustedOriginsIncludingCsrfReads() throws Exception {
        mvc.perform(get("/api/auth/csrf").header("Origin", "https://untrusted.example"))
                .andExpect(status().isForbidden());
        mvc.perform(options("/api/auth/profile").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "PATCH")) .andExpect(status().isForbidden());
    }
}
