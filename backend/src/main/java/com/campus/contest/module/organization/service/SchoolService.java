package com.campus.contest.module.organization.service;

import com.campus.contest.module.organization.dto.SchoolPageQuery;
import com.campus.contest.module.organization.vo.SchoolPageVO;

public interface SchoolService {

    SchoolPageVO pageSchools(SchoolPageQuery query);
}
