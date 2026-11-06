package com.liongirls.gradebattle.user;

import com.jayway.jsonpath.JsonPath;
import com.liongirls.gradebattle.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UserApiTests {
    private static final String SIGNUP = """
            {"loginId":"student1","password":"study1234!","nickname":"학생"}
            """;
    private static final String LOGIN = """
            {"loginId":"student1","password":"study1234!"}
            """;

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void clearUsers() {
        users.deleteAll();
    }

    @Test
    void signupHashesPasswordAndHidesItFromResponse() throws Exception {
        Client client = client();
        mvc.perform(client.withCsrf(post("/api/users/signup")).content(SIGNUP))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.totalXp").value(0))
                .andExpect(jsonPath("$.result.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.result.password").doesNotExist())
                .andExpect(jsonPath("$.result.passwordHash").doesNotExist());
        String hash = users.findByLoginId("student1").orElseThrow().getPasswordHash();
        assertThat(hash).isNotEqualTo("study1234!");
        assertThat(encoder.matches("study1234!", hash)).isTrue();
    }

    @Test
    void duplicateLoginIdIsRejected() throws Exception {
        Client client = signup();
        mvc.perform(client.withCsrf(post("/api/users/signup")).content(SIGNUP))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER_409"));
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void loginRotatesSessionAndAllowsOnlyOwnProfileThenLogoutRevokesAccess() throws Exception {
        Client client = signup();
        String previousId = client.session().getId();
        mvc.perform(client.withCsrf(post("/api/users/login")).content(LOGIN))
                .andExpect(status().isOk());
        assertThat(client.session().getId()).isNotEqualTo(previousId);
        mvc.perform(get("/api/users/me").session(client.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.loginId").value("student1"));
        mvc.perform(client.withCsrf(patch("/api/users/me")).content("{\"nickname\":\"용사\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.nickname").value("용사"));
        assertThat(users.findByLoginId("student1").orElseThrow().getNickname()).isEqualTo("용사");
        mvc.perform(client.withCsrf(post("/api/users/logout"))).andExpect(status().isOk());
        assertThat(client.session().isInvalid()).isTrue();
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordAndMissingUserReturnSameError() throws Exception {
        Client client = signup();
        for (String body : new String[]{LOGIN.replace("study1234!", "wrong1234!"), LOGIN.replace("student1", "nobody")}) {
            mvc.perform(client.withCsrf(post("/api/users/login")).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("USER_401"));
        }
        mvc.perform(get("/api/users/me").session(client.session())).andExpect(status().isUnauthorized());
    }

    @Test
    void signupDoesNotAutomaticallyLoginAndAnonymousCannotChangeProfile() throws Exception {
        Client client = signup();
        mvc.perform(get("/api/users/me").session(client.session())).andExpect(status().isUnauthorized());
        mvc.perform(client.withCsrf(patch("/api/users/me")).content("{\"nickname\":\"침입자\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void csrfIsRequiredForSignupLoginAndLogout() throws Exception {
        mvc.perform(post("/api/users/signup").contentType(MediaType.APPLICATION_JSON).content(SIGNUP))
                .andExpect(status().isForbidden());
        Client client = signup();
        mvc.perform(post("/api/users/login").session(client.session()).contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isForbidden());
        mvc.perform(client.withCsrf(post("/api/users/login")).content(LOGIN)).andExpect(status().isOk());
        mvc.perform(post("/api/users/logout").session(client.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/users/me").session(client.session())).andExpect(status().isOk());
    }

    @Test
    void invalidSignupAndPasswordOver72Utf8BytesAreRejected() throws Exception {
        Client client = client();
        for (String body : new String[]{SIGNUP.replace("student1", "a"), SIGNUP.replace("study1234!", "short"),
                SIGNUP.replace("학생", "   "), SIGNUP.replace("study1234!", "가".repeat(25))}) {
            mvc.perform(client.withCsrf(post("/api/users/signup")).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(users.count()).isZero();
    }

    @Test
    void authenticatedUserCannotAssignExperienceOrEditAnotherUser() throws Exception {
        Client first = signup();
        Client second = client();
        mvc.perform(second.withCsrf(post("/api/users/signup")).content(SIGNUP.replace("student1", "student2")))
                .andExpect(status().isCreated());
        mvc.perform(first.withCsrf(post("/api/users/login")).content(LOGIN)).andExpect(status().isOk());
        long otherId = users.findByLoginId("student2").orElseThrow().getUserId();
        mvc.perform(first.withCsrf(patch("/api/users/me")).content(
                        "{\"nickname\":\"용사\",\"totalXp\":9999,\"userId\":" + otherId + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.totalXp").value(0));
        assertThat(users.findById(otherId).orElseThrow().getNickname()).isEqualTo("학생");
    }

    private Client signup() throws Exception {
        Client client = client();
        mvc.perform(client.withCsrf(post("/api/users/signup")).content(SIGNUP)).andExpect(status().isCreated());
        return client;
    }

    private Client client() throws Exception {
        MvcResult result = mvc.perform(get("/api/users/csrf")).andExpect(status().isOk()).andReturn();
        String json = result.getResponse().getContentAsString();
        return new Client((MockHttpSession) result.getRequest().getSession(false),
                JsonPath.read(json, "$.result.headerName"), JsonPath.read(json, "$.result.token"));
    }

    private record Client(MockHttpSession session, String headerName, String token) {
        MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
            return request.session(session).header(headerName, token).contentType(MediaType.APPLICATION_JSON)
                    .with(servletRequest -> {
                        servletRequest.setRequestedSessionId(session.getId());
                        servletRequest.setRequestedSessionIdValid(true);
                        return servletRequest;
                    });
        }
    }
}
