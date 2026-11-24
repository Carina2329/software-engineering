package com.campus.contest.module.team.service;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.contest.entity.ContestEditionDO;
import com.campus.contest.module.contest.mapper.ContestAccessMapper;
import com.campus.contest.module.contest.service.ContestAuthorizationService;
import com.campus.contest.module.team.entity.TeamDO;
import com.campus.contest.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TeamAuthorizationService {

    private final CurrentUserProvider currentUserProvider;
    private final ContestAuthorizationService contestAuthorizationService;
    private final ContestAccessMapper contestAccessMapper;

    public Long currentUserId() {
        return currentUserProvider.requireUserId();
    }

    public boolean isGlobalOperator() {
        return contestAuthorizationService.currentScope().global();
    }

    public ContestEditionDO requireVisibleEdition(Long editionId) {
        var criteria = contestAuthorizationService.currentAccessCriteria();
        criteria.setEditionId(editionId);
        ContestEditionDO edition = contestAccessMapper.selectEditionById(criteria);
        if (edition == null) {
            throw new BizException(
                    ErrorCode.RESOURCE_NOT_FOUND.getCode(),
                    "届次不存在或对当前用户不可见"
            );
        }
        return edition;
    }

    public void requireReadable(TeamDO team) {
        Long userId = currentUserId();
        if (isGlobalOperator()
                || userId.equals(team.getLeaderId())
                || userId.equals(team.getTeacherId())) {
            return;
        }
        throw dataForbidden("无权查看该队伍");
    }

    public void requireLeader(TeamDO team) {
        if (!currentUserId().equals(team.getLeaderId())) {
            throw dataForbidden("只有队长可以操作该队伍");
        }
    }

    private BizException dataForbidden(String message) {
        return new BizException(ErrorCode.DATA_FORBIDDEN.getCode(), message);
    }
}
