package com.campus.contest.module.team.controller;

import com.campus.contest.common.Result;
import com.campus.contest.module.team.dto.TeamCreateRequest;
import com.campus.contest.module.team.dto.TeamPageQuery;
import com.campus.contest.module.team.dto.TeamUpdateRequest;
import com.campus.contest.module.team.service.TeamService;
import com.campus.contest.module.team.vo.TeamCreateVO;
import com.campus.contest.module.team.vo.TeamDetailVO;
import com.campus.contest.module.team.vo.TeamMemberVO;
import com.campus.contest.module.team.vo.TeamPageVO;
import com.campus.contest.module.team.vo.TeamVersionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    /** EP-026 */
    @GetMapping("/editions/{edition_id}/teams")
    @PreAuthorize("hasAuthority('team:list')")
    public Result<TeamPageVO> list(
            @PathVariable("edition_id") Long editionId,
            @ModelAttribute TeamPageQuery query
    ) {
        return Result.ok(teamService.list(editionId, query));
    }

    /** EP-027 */
    @PostMapping("/editions/{edition_id}/teams")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('team:create')")
    public Result<TeamCreateVO> create(
            @PathVariable("edition_id") Long editionId,
            @Valid @RequestBody TeamCreateRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return Result.ok(teamService.create(editionId, request, idempotencyKey));
    }

    /** EP-028 */
    @GetMapping("/teams/{team_id}")
    @PreAuthorize("hasAuthority('team:view')")
    public Result<TeamDetailVO> detail(@PathVariable("team_id") Long teamId) {
        return Result.ok(teamService.detail(teamId));
    }

    /** EP-029 */
    @PatchMapping("/teams/{team_id}")
    @PreAuthorize("hasAuthority('team:update')")
    public Result<TeamVersionVO> update(
            @PathVariable("team_id") Long teamId,
            @Valid @RequestBody TeamUpdateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return Result.ok(teamService.update(teamId, request, idempotencyKey));
    }

    /** EP-031 */
    @GetMapping("/teams/{team_id}/members")
    @PreAuthorize("hasAuthority('team:member:list')")
    public Result<List<TeamMemberVO>> members(@PathVariable("team_id") Long teamId) {
        return Result.ok(teamService.members(teamId));
    }

    /** EP-032 */
    @DeleteMapping("/teams/{team_id}/members/{member_id}")
    @PreAuthorize("hasAuthority('team:member:remove')")
    public Result<Void> removeMember(
            @PathVariable("team_id") Long teamId,
            @PathVariable("member_id") Long memberId,
            @RequestParam("version") Integer version,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        teamService.removeMember(teamId, memberId, version, idempotencyKey);
        return Result.ok(null);
    }
}
