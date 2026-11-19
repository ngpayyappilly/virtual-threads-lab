package com.cloudwalkers.threadsapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.threads.virtual.enabled=true")
@AutoConfigureMockMvc
@AutoConfigureMetrics
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ApiIntegrationTests {

	@Autowired
	MockMvc mvc;

	@Test
	void userPostCommentLifecycle() throws Exception {
		long userId = createUser("carol", "carol@example.com");

		// duplicate username -> 409
		mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
			.content(userJson("carol", "other@example.com")))
			.andExpect(status().isConflict());

		// invalid payload -> 400 with field errors
		mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
			.content(userJson("x", "not-an-email")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.username").exists())
			.andExpect(jsonPath("$.errors.email").exists());

		mvc.perform(put("/api/v1/users/" + userId).contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"username":"carol","email":"carol@example.com","displayName":"Carol C","bio":"hi"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("Carol C"));

		MvcResult created = mvc.perform(post("/api/v1/posts").contentType(MediaType.APPLICATION_JSON)
			.content("{\"authorId\":%d,\"content\":\"first thread\"}".formatted(userId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.commentCount").value(0))
			.andReturn();
		long postId = idOf(created);

		mvc.perform(post("/api/v1/posts/%d/comments".formatted(postId)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"authorId\":%d,\"content\":\"nice\"}".formatted(userId)))
			.andExpect(status().isCreated());

		mvc.perform(get("/api/v1/posts/" + postId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.commentCount").value(1));

		mvc.perform(put("/api/v1/posts/" + postId).contentType(MediaType.APPLICATION_JSON)
			.content("{\"content\":\"edited\"}"))
			.andExpect(jsonPath("$.content").value("edited"));

		mvc.perform(get("/api/v1/posts?authorId=" + userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1));

		// deleting the user cascades to posts and comments
		mvc.perform(delete("/api/v1/users/" + userId)).andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/posts/" + postId)).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/users/" + userId)).andExpect(status().isNotFound());
	}

	@Test
	void metricsAreExposed() throws Exception {
		mvc.perform(get("/api/v1/users")).andExpect(status().isOk());
		mvc.perform(get("/actuator/metrics/http.server.requests")).andExpect(status().isOk());
		mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
		mvc.perform(get("/actuator/metrics/threadsapp.http.requests.active")).andExpect(status().isOk());
		mvc.perform(get("/actuator/metrics/jvm.threads.virtual.pinned")).andExpect(status().isOk());
	}

	private long createUser(String username, String email) throws Exception {
		MvcResult result = mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
			.content(userJson(username, email)))
			.andExpect(status().isCreated())
			.andReturn();
		return idOf(result);
	}

	private static String userJson(String username, String email) {
		return "{\"username\":\"%s\",\"email\":\"%s\"}".formatted(username, email);
	}

	private static long idOf(MvcResult result) throws Exception {
		String body = result.getResponse().getContentAsString();
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"id\":(\\d+)").matcher(body);
		assertThat(m.find()).isTrue();
		return Long.parseLong(m.group(1));
	}

}
