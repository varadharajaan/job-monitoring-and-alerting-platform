package com.jobmonitor.platform.common.security;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class TenantContextTest {

    @Test
    void getTenantIdOptional_returnsEmptyWhenNull() {
        TenantContext ctx = new TenantContext();
        assertThat(ctx.getTenantIdOptional()).isEmpty();
    }

    @Test
    void getTenantIdOptional_returnsPresentWhenSet() {
        TenantContext ctx = new TenantContext();
        ctx.setTenantId("tenant-123");
        assertThat(ctx.getTenantIdOptional()).contains("tenant-123");
    }

    @Test
    void getTenantUUID_returnsEmptyWhenNull() {
        TenantContext ctx = new TenantContext();
        assertThat(ctx.getTenantUUID()).isEmpty();
    }

    @Test
    void getTenantUUID_returnsEmptyWhenBlank() {
        TenantContext ctx = new TenantContext();
        ctx.setTenantId("   ");
        assertThat(ctx.getTenantUUID()).isEmpty();
    }

    @Test
    void getTenantUUID_returnsUUIDWhenValid() {
        UUID expected = UUID.randomUUID();
        TenantContext ctx = new TenantContext();
        ctx.setTenantId(expected.toString());
        assertThat(ctx.getTenantUUID()).contains(expected);
    }

    @Test
    void getTenantUUID_throwsOnInvalidFormat() {
        TenantContext ctx = new TenantContext();
        ctx.setTenantId("not-a-uuid");
        assertThatThrownBy(ctx::getTenantUUID)
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getUserIdOptional_returnsEmptyWhenNull() {
        TenantContext ctx = new TenantContext();
        assertThat(ctx.getUserIdOptional()).isEmpty();
    }

    @Test
    void getUserIdOptional_returnsPresentWhenSet() {
        TenantContext ctx = new TenantContext();
        ctx.setUserId("user-42");
        assertThat(ctx.getUserIdOptional()).contains("user-42");
    }

    @Test
    void settersAndGetters_workCorrectly() {
        TenantContext ctx = new TenantContext();
        ctx.setTenantId("t-1");
        ctx.setUserId("u-1");
        ctx.setUsername("alice");

        assertThat(ctx.getTenantId()).isEqualTo("t-1");
        assertThat(ctx.getUserId()).isEqualTo("u-1");
        assertThat(ctx.getUsername()).isEqualTo("alice");
    }
}
