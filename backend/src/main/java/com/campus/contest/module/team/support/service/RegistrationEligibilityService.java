package com.campus.contest.module.team.support.service;

import com.campus.contest.module.team.support.model.RegistrationEligibilityContext;
import com.campus.contest.module.team.support.model.RegistrationEligibilityResult;

public interface RegistrationEligibilityService {

    RegistrationEligibilityResult checkCanRegister(
            RegistrationEligibilityContext context
    );
}
