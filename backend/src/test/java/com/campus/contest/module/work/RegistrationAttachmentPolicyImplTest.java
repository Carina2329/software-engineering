package com.campus.contest.module.work;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.work.entity.FileDO;
import com.campus.contest.module.work.mapper.FileMapper;
import com.campus.contest.module.work.mapper.RegistrationAttachmentUsageMapper;
import com.campus.contest.module.work.model.ValidatedRegistrationAttachment;
import com.campus.contest.module.work.service.impl.RegistrationAttachmentPolicyImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegistrationAttachmentPolicyImplTest {

    private final FileMapper fileMapper = mock(FileMapper.class);
    private final RegistrationAttachmentUsageMapper usageMapper =
            mock(RegistrationAttachmentUsageMapper.class);
    private final RegistrationAttachmentPolicyImpl policy =
            new RegistrationAttachmentPolicyImpl(fileMapper, usageMapper);

    @Test
    void captainCanUseOwnedCompleteFileWithoutLeakingStoragePath() {
        FileDO file = file(7L, 2L, "application/pdf", 1024L);
        when(fileMapper.selectById(7L)).thenReturn(file);
        when(usageMapper.countOtherTeamAssociations(7L, 10L)).thenReturn(0L);

        List<ValidatedRegistrationAttachment> result =
                policy.validateForCaptain(2L, 10L, List.of(7L));

        assertThat(result).containsExactly(new ValidatedRegistrationAttachment(
                7L,
                "entry.pdf",
                "application/pdf",
                1024L,
                "REGISTRATION_ATTACHMENT"
        ));
    }

    @Test
    void missingOrDeletedFileIsNotVisible() {
        when(fileMapper.selectById(7L)).thenReturn(null);
        assertNotFound(() -> policy.validateForCaptain(2L, 10L, List.of(7L)));

        FileDO deleted = file(8L, 2L, "application/pdf", 1024L);
        deleted.setDeleted(1);
        when(fileMapper.selectById(8L)).thenReturn(deleted);
        assertNotFound(() -> policy.validateForCaptain(2L, 10L, List.of(8L)));
    }

    @Test
    void otherUsersFileCannotBeAttached() {
        when(fileMapper.selectById(7L)).thenReturn(file(7L, 3L, "application/pdf", 1024L));

        assertNotFound(() -> policy.validateForCaptain(2L, 10L, List.of(7L)));
    }

    @Test
    void invalidSizeAndDuplicateIdsAreRejected() {
        assertInvalid(() -> policy.validateForCaptain(2L, 10L, List.of()));
        assertInvalid(() -> policy.validateForCaptain(2L, 10L, List.of(7L, 7L)));

        when(fileMapper.selectById(7L)).thenReturn(file(
                7L,
                2L,
                "application/pdf",
                5L * 1024 * 1024 * 1024 + 1
        ));
        assertInvalid(() -> policy.validateForCaptain(2L, 10L, List.of(7L)));
    }

    @Test
    void fileAssociatedWithAnotherTeamCannotBeReused() {
        when(fileMapper.selectById(7L)).thenReturn(file(7L, 2L, "application/pdf", 1024L));
        when(usageMapper.countOtherTeamAssociations(7L, 10L)).thenReturn(1L);

        assertNotFound(() -> policy.validateForCaptain(2L, 10L, List.of(7L)));
    }

    private FileDO file(Long id, Long uploaderId, String mimeType, long size) {
        FileDO file = new FileDO();
        file.setId(id);
        file.setOriginalName("entry.pdf");
        file.setMimeType(mimeType);
        file.setFileSize(size);
        file.setStorageType(1);
        file.setUploaderId(uploaderId);
        file.setStoragePath("private/storage/path");
        file.setDeleted(0);
        return file;
    }

    private void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
    }

    private void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
    }
}
