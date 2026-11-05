package com.livemcq.web;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import com.livemcq.academic.AcademicDtos;
import com.livemcq.auth.LoginResponse;
import com.livemcq.auth.UserView;
import com.livemcq.exam.ExamDtos;
import com.livemcq.exam.ResultDtos;
import com.livemcq.exam.StudentExamDtos;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.lang.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * Describes the API for the generated clients (Next.js, Flutter).
 * Springdoc reads our controllers with reflection; this class only adds what reflection cannot know.
 */
@Configuration
public class OpenApiConfig {

    static {
        // the logged-in user is injected by Spring Security, it is not something the client sends
        SpringDocUtils.getConfig().addAnnotationsToIgnore(AuthenticationPrincipal.class);
    }

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI liveMcqOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Live MCQ API").version("0.1.0")
                        .description("Exam platform for schools and coaching centres. "
                                + "Log in at POST /api/auth/login, then send the token as 'Authorization: Bearer <token>'."))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                // every operation needs the token unless it says otherwise (@SecurityRequirements on login/register)
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /**
     * Stable operation names: ExamAdminController.create -> "examAdminCreate".
     * Generated clients turn the operationId into a function name, so it must be unique and must not change by accident.
     */
    @Bean
    OperationCustomizer stableOperationIds() {
        return (operation, handlerMethod) -> {
            String controller = handlerMethod.getBeanType().getSimpleName().replace("Controller", "");
            String method = handlerMethod.getMethod().getName();
            operation.setOperationId(Character.toLowerCase(controller.charAt(0)) + controller.substring(1)
                    + Character.toUpperCase(method.charAt(0)) + method.substring(1));
            return operation;
        };
    }

    /** Every error, whatever its status, has the ApiError shape (see GlobalExceptionHandler). */
    @Bean
    OpenApiCustomizer errorShape() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            ModelConverters.getInstance().readAll(ApiError.class).forEach(openApi.getComponents()::addSchemas);
            Schema<?> ref = new Schema<>().$ref("#/components/schemas/ApiError");
            ApiResponse error = new ApiResponse().description("Error (see the code field)")
                    .content(new Content().addMediaType("application/json", new MediaType().schema(ref)));
            if (openApi.getPaths() != null) {
                openApi.getPaths().values().forEach(path -> path.readOperations()
                        .forEach(op -> op.getResponses().addApiResponse("default", error)));
            }
            markRequiredAndNullable(openApi);
        };
    }

    private static final List<Class<?>> DTO_HOLDERS =
            List.of(AcademicDtos.class, ExamDtos.class, ResultDtos.class, StudentExamDtos.class);
    private static final List<Class<?>> TOP_LEVEL_DTOS = List.of(LoginResponse.class, UserView.class, ApiError.class);

    /**
     * Generated clients need to know which response fields are always present. By default every field looks
     * optional (name?: string), which forces needless null-checks. Here: in every RESPONSE record, a field is
     * required unless it is marked @Nullable in the Java code, in which case it is also flagged nullable.
     * (Request records keep the rules Bean Validation already described.)
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void markRequiredAndNullable(OpenAPI openApi) {
        Map<String, Class<?>> records = new HashMap<>();
        for (Class<?> holder : DTO_HOLDERS) {
            for (Class<?> c : holder.getDeclaredClasses()) {
                if (c.isRecord()) records.put(c.getSimpleName(), c);
            }
        }
        for (Class<?> c : TOP_LEVEL_DTOS) {
            records.put(c.getSimpleName(), c);
            for (Class<?> nested : c.getDeclaredClasses()) {
                if (nested.isRecord()) records.put(nested.getSimpleName(), nested);
            }
        }
        Map<String, Schema> schemas = openApi.getComponents().getSchemas();
        schemas.forEach((name, schema) -> {
            Class<?> type = records.get(name);
            if (type == null || name.endsWith("Request") || schema.getProperties() == null) return;
            List<String> required = new ArrayList<>();
            for (RecordComponent rc : type.getRecordComponents()) {
                Schema<?> property = (Schema<?>) schema.getProperties().get(rc.getName());
                if (property == null) continue;
                if (rc.getAccessor().isAnnotationPresent(Nullable.class)) {
                    if (property.get$ref() == null) {
                        // springdoc 2.x writes OpenAPI 3.1, where "nullable" does not exist: the type becomes
                        // ["string", "null"]. (Older 3.0 documents use the nullable flag instead.)
                        if (property.getTypes() != null && !property.getTypes().isEmpty()) property.addType("null");
                        else property.setNullable(true);
                    }
                } else {
                    required.add(rc.getName());
                }
            }
            schema.setRequired(required);
        });
    }
}
