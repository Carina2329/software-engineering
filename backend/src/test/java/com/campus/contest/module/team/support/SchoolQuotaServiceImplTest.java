package com.campus.contest.module.team.support;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.team.support.mapper.RegistrationSupportMapper;
import com.campus.contest.module.team.support.mapper.RegistrationSupportMapper.QuotaRow;
import com.campus.contest.module.team.support.mapper.RegistrationSupportMapper.RegistrationRow;
import com.campus.contest.module.team.support.model.SchoolQuotaReservation;
import com.campus.contest.module.team.support.service.impl.SchoolQuotaServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchoolQuotaServiceImplTest {

    private RegistrationSupportMapper mapper;
    private ObjectMapper objectMapper;
    private SchoolQuotaServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(RegistrationSupportMapper.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new SchoolQuotaServiceImpl(
                mapper,
                objectMapper,
                Clock.fixed(Instant.parse("2026-11-24T10:00:00Z"), ZoneId.of("Asia/Shanghai"))
        );
    }

    @Test
    void missingQuotaConfigurationMeansUnlimitedWithoutOccupying() {
        when(mapper.selectRegistrationForUpdate(50L)).thenReturn(registration(0, null));
        when(mapper.selectQuota(20L, 2L)).thenReturn(null);
        when(mapper.selectQuotaForUpdate(20L, 2L)).thenReturn(null);
        when(mapper.updateQuotaSnapshot(eqLong(50L), eqLong(2L), anyString(), eqInt(0)))
                .thenReturn(1);

        SchoolQuotaReservation result = service.occupy(50L, 20L, 2L);

        assertThat(result.limited()).isFalse();
        assertThat(result.held()).isFalse();
        verify(mapper, never()).incrementQuota(anyLong());
    }

    @Test
    void limitedQuotaOccupiesOnceAndStoresSnapshot() {
        when(mapper.selectRegistrationForUpdate(50L)).thenReturn(registration(0, null));
        when(mapper.selectQuota(20L, 2L)).thenReturn(new QuotaRow(3L, 2, 1));
        when(mapper.selectQuotaForUpdate(20L, 2L)).thenReturn(new QuotaRow(3L, 2, 1));
        when(mapper.incrementQuota(3L)).thenReturn(1);
        when(mapper.updateQuotaSnapshot(eqLong(50L), eqLong(2L), anyString(), eqInt(1)))
                .thenReturn(1);

        SchoolQuotaReservation result = service.occupy(50L, 20L, 2L);

        assertThat(result).isEqualTo(new SchoolQuotaReservation(3L, 2L, 2, 2, true, true));
        verify(mapper).incrementQuota(3L);
    }

    @Test
    void fullOrPreviouslyHeldQuotaHasDeterministicResult() {
        when(mapper.selectRegistrationForUpdate(50L)).thenReturn(registration(0, null));
        when(mapper.selectQuota(20L, 2L)).thenReturn(new QuotaRow(3L, 2, 2));
        when(mapper.selectQuotaForUpdate(20L, 2L)).thenReturn(new QuotaRow(3L, 2, 2));

        assertConflict(() -> service.occupy(50L, 20L, 2L));

        when(mapper.selectRegistrationForUpdate(51L)).thenReturn(registration(1, 2L));
        when(mapper.selectQuota(20L, 2L)).thenReturn(new QuotaRow(3L, 2, 2));
        SchoolQuotaReservation repeated = service.occupy(51L, 20L, 2L);
        assertThat(repeated.held()).isTrue();
        verify(mapper, never()).incrementQuota(99L);
    }

    @Test
    void releaseUsesQuotaHeldFlagAndIsIdempotent() {
        when(mapper.selectRegistrationForUpdate(50L)).thenReturn(registration(1, 2L));
        when(mapper.selectQuotaForUpdate(20L, 2L)).thenReturn(new QuotaRow(3L, 2, 2));
        when(mapper.decrementQuota(3L)).thenReturn(1);
        when(mapper.clearQuotaHeld(50L)).thenReturn(1);

        service.release(50L);
        verify(mapper).decrementQuota(3L);

        when(mapper.selectRegistrationForUpdate(51L)).thenReturn(registration(0, 2L));
        service.release(51L);
        verify(mapper, never()).decrementQuota(99L);
    }

    @Test
    void inconsistentHeldQuotaFailsInsteadOfSilentlyLosingCapacity() {
        when(mapper.selectRegistrationForUpdate(50L)).thenReturn(registration(1, 2L));
        when(mapper.selectQuotaForUpdate(20L, 2L)).thenReturn(null);

        assertConflict(() -> service.release(50L));
    }

    private RegistrationRow registration(int held, Long schoolId) {
        return new RegistrationRow(
                50L, 10L, 20L, "PENDING_TEACHER", "[]", held, schoolId
        );
    }

    private void assertConflict(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.BUSINESS_CONFLICT.getCode()));
    }

    private long eqLong(long value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }

    private int eqInt(int value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
