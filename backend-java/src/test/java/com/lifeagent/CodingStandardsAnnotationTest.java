package com.lifeagent;

import com.lifeagent.common.ApiResponse;
import com.lifeagent.controller.SystemController;
import com.lifeagent.dto.ContextPackage;
import com.lifeagent.dto.SystemInfoResponse;
import com.lifeagent.dto.TurnResolutionRequest;
import com.lifeagent.dto.TurnResolutionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CodingStandardsAnnotationTest {

    @Test
    void shouldDocumentControllerAndOperationInChinese() throws NoSuchMethodException {
        Tag tag = SystemController.class.getAnnotation(Tag.class);
        Method method = SystemController.class.getMethod("getSystemInfo");
        Operation operation = method.getAnnotation(Operation.class);

        assertThat(tag).isNotNull();
        assertThat(tag.name()).isEqualTo("系统信息");
        assertThat(operation).isNotNull();
        assertThat(operation.summary()).isEqualTo("查询系统信息");
    }

    @Test
    void shouldDocumentEveryDtoFieldWithSchemaDescription() {
        Stream.of(
                        ApiResponse.class,
                        ContextPackage.class,
                        SystemInfoResponse.class,
                        TurnResolutionRequest.class,
                        TurnResolutionResponse.class
                )
                .flatMap(type -> Stream.of(type.getDeclaredFields()))
                .forEach(this::assertHasSchemaDescription);
    }

    private void assertHasSchemaDescription(Field field) {
        Schema schema = field.getAnnotation(Schema.class);

        assertThat(schema)
                .as("字段 %s.%s 必须声明 @Schema", field.getDeclaringClass().getSimpleName(), field.getName())
                .isNotNull();
        assertThat(schema.description())
                .as("字段 %s.%s 必须声明中文说明", field.getDeclaringClass().getSimpleName(), field.getName())
                .isNotBlank();
    }
}
