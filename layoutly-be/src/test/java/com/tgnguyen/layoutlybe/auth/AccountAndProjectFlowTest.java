package com.tgnguyen.layoutlybe.auth;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Chay voi H2 trong bo nho, khong can Neon. Di dung quy trinh cua frontend: /csrf truoc, roi POST kem header. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:layoutly-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "layoutly.auth.bcrypt-strength=4",
        "layoutly.data.directory=target/test-data",
        "layoutly.cache.directory=target/test-cache"
})
@AutoConfigureMockMvc
class AccountAndProjectFlowTest {
    private static final String PASSWORD = "correct-horse-battery";

    private record Csrf(Cookie cookie, String token) {
    }

    @Autowired
    MockMvc mvc;

    @Test
    void capabilitiesFollowFrontendContract() throws Exception {
        mvc.perform(get("/api/auth/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractVersion").value(1))
                .andExpect(jsonPath("$.login").value(true))
                .andExpect(jsonPath("$.registration").value(true))
                .andExpect(jsonPath("$.passwordReset").value(false));
    }

    @Test
    void csrfEndpointReturnsHeaderNameAndToken() throws Exception {
        mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void unsafeRequestsWithoutCsrfAreRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"x\"}"))
                .andExpect(status().isForbidden());
        Csrf csrf = csrf();
        // Co cookie nhung header sai.
        mvc.perform(post("/api/auth/login").cookie(csrf.cookie()).header("X-CSRF-TOKEN", "sai")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerRejectsShortPasswordBadEmailAndMissingTerms() throws Exception {
        mvc.perform(withCsrf(post("/api/auth/register")).contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("Tan", "short@example.com", "short", "1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
        mvc.perform(withCsrf(post("/api/auth/register")).contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("Tan", "not-an-email", PASSWORD, "1")))
                .andExpect(status().isBadRequest());
        mvc.perform(withCsrf(post("/api/auth/register")).contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("Tan", "terms@example.com", PASSWORD, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerMeDuplicateAndLogout() throws Exception {
        Cookie session = register("flow@example.com");

        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("flow@example.com"));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());

        mvc.perform(withCsrf(post("/api/auth/register")).contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("Tan", "FLOW@example.com", PASSWORD, "1")))
                .andExpect(status().isConflict());

        mvc.perform(withCsrf(post("/api/auth/logout")).cookie(session)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginSucceedsWithCorrectPasswordAndLocksAfterFiveFailures() throws Exception {
        register("login@example.com");

        mvc.perform(withCsrf(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("login@example.com", PASSWORD, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").isNotEmpty());

        for (int i = 0; i < 5; i++) {
            mvc.perform(withCsrf(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody("login@example.com", "wrong-password-123", false)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(withCsrf(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("login@example.com", PASSWORD, false)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void projectsRequireLogin() throws Exception {
        mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
        mvc.perform(withCsrf(post("/api/projects")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void projectLifecycleIsPrivateToOwner() throws Exception {
        Cookie owner = register("owner@example.com");
        Cookie other = register("other@example.com");

        MvcResult created = mvc.perform(withCsrf(post("/api/projects")).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Landing\",\"framework\":\"react\",\"files\":["
                                + "{\"path\":\"src/App.jsx\",\"content\":\"export default () => null\"}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.project.framework").value("REACT"))
                .andExpect(jsonPath("$.files.length()").value(1))
                .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.project.id");

        // Them file moi, giu file cu (replaceFiles mac dinh false).
        mvc.perform(withCsrf(put("/api/projects/" + id)).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[{\"path\":\"styles.css\",\"content\":\"body{margin:0}\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files.length()").value(2));

        // replaceFiles=true: chi con file gui len.
        mvc.perform(withCsrf(put("/api/projects/" + id)).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"replaceFiles\":true,\"files\":[{\"path\":\"styles.css\",\"content\":\"a{color:red}\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files.length()").value(1))
                .andExpect(jsonPath("$.files[0].content").value("a{color:red}"));

        mvc.perform(get("/api/projects").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        byte[] zip = mvc.perform(get("/api/projects/" + id + "/download").cookie(owner))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(zip).startsWith((byte) 'P', (byte) 'K');

        // Nguoi khac khong thay, khong xoa duoc.
        mvc.perform(get("/api/projects/" + id).cookie(other)).andExpect(status().isNotFound());
        mvc.perform(withCsrf(delete("/api/projects/" + id)).cookie(other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/projects").cookie(other)).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(withCsrf(delete("/api/projects/" + id)).cookie(owner)).andExpect(status().isNoContent());
        mvc.perform(get("/api/projects/" + id).cookie(owner)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnsafeFilePaths() throws Exception {
        Cookie owner = register("paths@example.com");
        for (String path : new String[]{"../evil.txt", "/etc/passwd", "a//b.txt", "C:/x.txt"}) {
            mvc.perform(withCsrf(post("/api/projects")).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"x\",\"files\":[{\"path\":\"" + path + "\",\"content\":\"\"}]}"))
                    .andExpect(status().isBadRequest());
        }
    }

    /** Lay cookie + token CSRF nhu frontend lam o buoc dau cua moi lan gui form. */
    private Csrf csrf() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        Cookie cookie = result.getResponse().getCookie(CsrfFilter.COOKIE);
        assertThat(cookie).isNotNull();
        return new Csrf(cookie, token);
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder) throws Exception {
        Csrf csrf = csrf();
        return builder.cookie(csrf.cookie()).header(CsrfFilter.HEADER, csrf.token());
    }

    private Cookie register(String email) throws Exception {
        MvcResult result = mvc.perform(withCsrf(post("/api/auth/register")).contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("Tan", email, PASSWORD, "1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.id").isNotEmpty())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie(AuthService.SESSION_COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        return cookie;
    }

    private static String registerBody(String name, String email, String password, String termsVersion) {
        return "{\"displayName\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\""
                + (termsVersion == null ? "" : ",\"acceptedTermsVersion\":\"" + termsVersion + "\"") + "}";
    }

    private static String loginBody(String email, String password, boolean rememberMe) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"rememberMe\":" + rememberMe + "}";
    }
}
