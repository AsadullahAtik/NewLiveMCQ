package com.livemcq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The API CONTRACT. The OpenAPI document is generated from the controllers; this test pins down what must be
 * in it, so removing or renaming a route by accident fails the build, and then saves the document to
 * docs/openapi.json for the Next.js and Flutter clients to be generated from.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiContractTest {

    /** Every operation of the API, as "METHOD path". Adding a route means adding it here on purpose. */
    private static final Set<String> EXPECTED = new TreeSet<>(Set.of(
            "POST /api/auth/login", "POST /api/auth/register", "GET /api/auth/me",
            "GET /api/public/institutions/{slug}/classes",
            "GET /api/admin/classes", "POST /api/admin/classes", "POST /api/admin/classes/{classId}/sections",
            "GET /api/admin/students", "PATCH /api/admin/students/{studentId}/enrollment",
            "GET /api/admin/exams", "POST /api/admin/exams", "GET /api/admin/exams/{examId}",
            "DELETE /api/admin/exams/{examId}", "PUT /api/admin/exams/{examId}/questions",
            "GET /api/admin/summary", "POST /api/admin/exams/{examId}/publish", "DELETE /api/admin/exams/{examId}/publish",
            "POST /api/admin/exams/{examId}/bonus", "GET /api/admin/exams/{examId}/leaderboard",
            "GET /api/admin/exams/{examId}/attempts/{studentId}",
            "GET /api/admin/classes/{classId}/sections/{sectionId}/students", "GET /api/admin/students/{studentId}/report",
            "GET /api/student/exams", "POST /api/student/exams/{examId}/start", "POST /api/student/exams/{examId}/submit",
            "GET /api/student/exams/{examId}/result", "GET /api/student/exams/{examId}/leaderboard",
            "GET /api/student/leaderboard/overall"));

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private JsonNode spec() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test
    void theDocumentListsExactlyTheExpectedOperations() throws Exception {
        Set<String> actual = new TreeSet<>();
        spec().get("paths").fields().forEachRemaining(path -> path.getValue().fieldNames().forEachRemaining(m -> {
            if (HTTP_METHODS.contains(m)) actual.add(m.toUpperCase() + " " + path.getKey());
        }));
        assertEquals(EXPECTED, actual);
    }

    @Test
    void operationIdsAreUniqueAndStable() throws Exception {
        Set<String> ids = new HashSet<>();
        int operations = 0;
        for (JsonNode op : operations(spec())) {
            operations++;
            String id = op.get("operationId").asText();
            assertTrue(ids.add(id), "duplicate operationId " + id);
            assertFalse(id.matches(".*_\\d+$"), "springdoc had to disambiguate: " + id);
        }
        assertEquals(EXPECTED.size(), operations);
        assertTrue(ids.contains("authLogin") && ids.contains("examAdminCreate") && ids.contains("studentExamSubmit"), ids.toString());
    }

    @Test
    void everythingNeedsTheBearerTokenExceptLoginRegisterAndPublicClasses() throws Exception {
        JsonNode spec = spec();
        assertEquals("bearer", spec.at("/components/securitySchemes/bearerAuth/scheme").asText());
        assertEquals("JWT", spec.at("/components/securitySchemes/bearerAuth/bearerFormat").asText());
        assertTrue(spec.get("security").toString().contains("bearerAuth"), "global requirement");

        Set<String> open = Set.of("authLogin", "authRegister", "academicPublicClasses");
        for (JsonNode op : operations(spec)) {
            String id = op.get("operationId").asText();
            boolean clearedSecurity = op.has("security") && op.get("security").isEmpty();
            assertEquals(open.contains(id), clearedSecurity, id + " security");
        }
    }

    @Test
    void everyOperationDocumentsTheSameErrorShapeAndHidesTheInjectedUser() throws Exception {
        JsonNode spec = spec();
        assertTrue(spec.at("/components/schemas/ApiError/properties/code").isObject());
        for (JsonNode op : operations(spec)) {
            assertEquals("#/components/schemas/ApiError",
                    op.at("/responses/default/content/application~1json/schema/$ref").asText(), op.get("operationId").asText());
            if (op.has("parameters")) {
                for (JsonNode p : op.get("parameters")) {
                    assertFalse(Set.of("me", "principal").contains(p.get("name").asText()),
                            op.get("operationId").asText() + " leaks the injected user as a parameter");
                }
            }
        }
    }

    @Test
    void requestAndResponseBodiesAreDescribed() throws Exception {
        JsonNode spec = spec();
        assertTrue(spec.at("/components/schemas/LoginRequest/properties/roll").isObject());
        assertTrue(spec.at("/components/schemas/CreateExamRequest/properties/questions").isObject());
        assertTrue(spec.at("/components/schemas/LoginResponse/properties/token").isObject());
        assertTrue(spec.at("/components/schemas/PaperView/properties/questions").isObject());
        assertFalse(spec.at("/components/schemas/PaperQuestion/properties").has("correctIdx"),
                "the paper a student receives must not describe the correct answer");
        assertTrue(spec.at("/components/schemas/ReviewItem/properties/correctIdx").isObject());
        assertTrue(spec.at("/components/schemas/UserView/properties/role/enum").toString().contains("TEACHER"));
    }

    @Test
    void responseFieldsAreRequiredUnlessMarkedNullable() throws Exception {
        JsonNode spec = spec();
        Set<String> userView = names(spec.at("/components/schemas/UserView/required"));
        assertTrue(userView.containsAll(Set.of("id", "name", "roll", "role", "institution")), userView.toString());
        assertFalse(userView.contains("className"), "a staff user has no class");
        JsonNode className = spec.at("/components/schemas/UserView/properties/className");
        assertTrue(className.path("nullable").asBoolean() || className.path("type").toString().contains("null"),
                "className must be described as nullable: " + className);

        assertFalse(names(spec.at("/components/schemas/ReviewItem/required")).contains("chosenIdx"));
        assertTrue(names(spec.at("/components/schemas/ReviewItem/required")).containsAll(Set.of("body", "options", "correctIdx")));
        Set<String> summary = names(spec.at("/components/schemas/ExamSummary/required"));
        assertTrue(summary.containsAll(Set.of("id", "title", "fullMarks", "published", "attempts")) && !summary.contains("publishedAt"));
        Set<String> err = names(spec.at("/components/schemas/ApiError/required"));
        assertTrue(err.contains("code") && err.contains("message") && !err.contains("fieldErrors"), err.toString());
        assertTrue(names(spec.at("/components/schemas/BoardRow/required")).contains("rank"));
        assertFalse(names(spec.at("/components/schemas/BoardRow/required")).contains("userId"));
    }

    private static Set<String> names(JsonNode array) {
        Set<String> out = new TreeSet<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    @Test
    void theDocumentIsSavedForTheClientGenerators() throws Exception {
        JsonNode spec = spec();
        Path file = Path.of("docs", "openapi.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, json.writerWithDefaultPrettyPrinter().writeValueAsString(spec) + "\n", StandardCharsets.UTF_8);
        assertTrue(Files.size(file) > 5_000, "suspiciously small: " + file.toAbsolutePath());
    }

    /** All operation objects (the GET/POST/... entries) of the document. */
    private static Iterable<JsonNode> operations(JsonNode spec) {
        Set<JsonNode> ops = new HashSet<>();
        for (Iterator<Map.Entry<String, JsonNode>> paths = spec.get("paths").fields(); paths.hasNext(); ) {
            JsonNode item = paths.next().getValue();
            for (Iterator<Map.Entry<String, JsonNode>> it = item.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                if (HTTP_METHODS.contains(e.getKey())) ops.add(e.getValue());
            }
        }
        return ops;
    }
}
