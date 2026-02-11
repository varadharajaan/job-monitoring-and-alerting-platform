package com.jobmonitor.platform.common.model;

import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BaseEntityTest {

    // Concrete subclass for testing the abstract BaseEntity
    static class TestEntity extends BaseEntity {
        private String name;
    }

    @Test
    void gettersAndSetters_workCorrectly() {
        TestEntity entity = new TestEntity();
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        entity.setId(id);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now.plusSeconds(10));
        entity.setVersion(1L);
        entity.setTenantId("tenant-abc");

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getUpdatedAt()).isAfter(now);
        assertThat(entity.getVersion()).isEqualTo(1L);
        assertThat(entity.getTenantId()).isEqualTo("tenant-abc");
    }

    @Test
    void id_hasUUIDGenerationStrategy() throws NoSuchFieldException {
        Field idField = BaseEntity.class.getDeclaredField("id");
        GeneratedValue annotation = idField.getAnnotation(GeneratedValue.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.strategy()).isEqualTo(GenerationType.UUID);
    }

    @Test
    void id_isNotUpdatable() throws NoSuchFieldException {
        Field idField = BaseEntity.class.getDeclaredField("id");
        Column column = idField.getAnnotation(Column.class);
        assertThat(column.updatable()).isFalse();
        assertThat(column.nullable()).isFalse();
    }

    @Test
    void createdAt_hasCreatedDateAnnotation() throws NoSuchFieldException {
        Field field = BaseEntity.class.getDeclaredField("createdAt");
        assertThat(field.getAnnotation(CreatedDate.class)).isNotNull();
        Column column = field.getAnnotation(Column.class);
        assertThat(column.updatable()).isFalse();
    }

    @Test
    void updatedAt_hasLastModifiedDateAnnotation() throws NoSuchFieldException {
        Field field = BaseEntity.class.getDeclaredField("updatedAt");
        assertThat(field.getAnnotation(LastModifiedDate.class)).isNotNull();
    }

    @Test
    void version_hasVersionAnnotation() throws NoSuchFieldException {
        Field field = BaseEntity.class.getDeclaredField("version");
        assertThat(field.getAnnotation(Version.class)).isNotNull();
    }

    @Test
    void tenantId_isNotNullable() throws NoSuchFieldException {
        Field field = BaseEntity.class.getDeclaredField("tenantId");
        Column column = field.getAnnotation(Column.class);
        assertThat(column.nullable()).isFalse();
    }

    @Test
    void class_isMappedSuperclass() {
        assertThat(BaseEntity.class.getAnnotation(MappedSuperclass.class)).isNotNull();
    }
}
