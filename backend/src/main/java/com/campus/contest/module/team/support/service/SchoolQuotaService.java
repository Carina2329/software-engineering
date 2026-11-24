package com.campus.contest.module.team.support.service;

import com.campus.contest.module.team.support.model.SchoolQuotaReservation;

public interface SchoolQuotaService {

    SchoolQuotaReservation occupy(
            Long registrationId,
            Long editionId,
            Long schoolId
    );

    void release(Long registrationId);
}
