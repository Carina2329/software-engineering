package com.campus.contest.module.work;

import com.campus.contest.module.work.mapper.FileAccessScopeMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FileAccessScopeMapper 的 SQL 契约测试。
 *
 * 这三条 SQL 决定 EP-050 的数据范围是否越权，而判定条件写在注解字符串里，
 * 改动时最容易漏条件（例如 TEAM 只过滤 team_member.deleted，忘了 status 和 team.deleted）。
 * 这里直接把注解上的 SQL 取出来做断言，条件被删掉或写松，测试立刻失败。
 */
class FileAccessScopeMapperSqlTest {

    @Test
    @DisplayName("TEAM SQL 必须过滤已删除成员、离队成员和已删除队伍")
    void teamSqlFiltersDeletedMemberLeftMemberAndDeletedTeam() throws Exception {
        String sql = sqlOf("countTeamAccess");

        // 队伍本身必须 join 且未逻辑删除，否则已解散队伍的旧成员仍可访问
        assertThat(sql).contains("join team t on t.id = ws.team_id");
        assertThat(sql).contains("t.deleted = 0");

        // 成员关系必须 join team_member，且同时过滤 deleted 与 status
        assertThat(sql).contains("join team_member tm on tm.team_id = t.id");
        assertThat(sql).contains("tm.deleted = 0");
        assertThat(sql)
                .as("在队状态必须为 1(IN)，否则离队成员仍可访问")
                .contains("tm.status = 1");

        // 上下游链路仍然要求作品版本未删除
        assertThat(sql).contains("join work_submission ws on ws.id = wf.submission_id and ws.deleted = 0");

        // 只允许对当前用户命中的单条记录计数，不允许出现放宽条件的 OR
        assertThat(sql).contains("wf.file_id = #{fileid}");
        assertThat(sql).contains("tm.user_id = #{userid}");
        assertThat(sql).doesNotContain(" or ");
    }

    @Test
    @DisplayName("GUIDED SQL 要求队伍未逻辑删除，并且按 teacher_id 命中")
    void guidedSqlRequiresUndeletedTeam() throws Exception {
        String sql = sqlOf("countGuidedAccess");

        assertThat(sql).contains("join team t");
        assertThat(sql).contains("t.deleted = 0");
        assertThat(sql).contains("t.teacher_id = #{userid}");
        assertThat(sql).contains("ws.deleted = 0");
    }

    @Test
    @DisplayName("ASSIGNED SQL 要求评审指派记录未逻辑删除")
    void assignedSqlRequiresUndeletedAssignment() throws Exception {
        String sql = sqlOf("countAssignedAccess");

        assertThat(sql).contains("join review_assignment ra");
        assertThat(sql).contains("ra.deleted = 0");
        assertThat(sql).contains("ra.reviewer_id = #{userid}");
    }

    private String sqlOf(String methodName) throws Exception {
        Method method = FileAccessScopeMapper.class.getMethod(methodName, Long.class, Long.class);
        Select select = method.getAnnotation(Select.class);
        assertThat(select)
                .as("%s 必须保留 @Select 注解，SQL 不允许改成 XML 后失去契约测试", methodName)
                .isNotNull();
        return String.join("\n", select.value())
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
}
