package com.campus.contest.module.team.service;

import com.campus.contest.module.team.dto.TeamCreateRequest;
import com.campus.contest.module.team.dto.TeamPageQuery;
import com.campus.contest.module.team.dto.TeamUpdateRequest;
import com.campus.contest.module.team.vo.TeamCreateVO;
import com.campus.contest.module.team.vo.TeamDetailVO;
import com.campus.contest.module.team.vo.TeamMemberVO;
import com.campus.contest.module.team.vo.TeamPageVO;
import com.campus.contest.module.team.vo.TeamVersionVO;

import java.util.List;

public interface TeamService {

    TeamPageVO list(Long editionId, TeamPageQuery query);

    TeamCreateVO create(Long editionId, TeamCreateRequest request, String idempotencyKey);

    TeamDetailVO detail(Long teamId);

    TeamVersionVO update(Long teamId, TeamUpdateRequest request, String idempotencyKey);

    List<TeamMemberVO> members(Long teamId);

    void removeMember(Long teamId, Long memberId, Integer version, String idempotencyKey);
}
