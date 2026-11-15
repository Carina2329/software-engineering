package com.campus.contest.module.system.vo;

import com.campus.contest.module.system.entity.SysMenuDO;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * EP-076 / EP-008 权限资源节点。
 * menu_type 与前端 frontend/src/types/rbac.ts 对齐，统一输出
 * DIRECTORY（目录，库内 1）、MENU（菜单，库内 2）、BUTTON（按钮/API，库内 3）。
 */
@Data
public class PermissionResourceVO {

    private static final int TYPE_DIRECTORY = 1;
    private static final int TYPE_MENU = 2;
    private static final int TYPE_BUTTON = 3;

    private String id;

    @JsonProperty("parent_id")
    private String parentId;

    @JsonProperty("menu_name")
    private String menuName;

    @JsonProperty("menu_type")
    private String menuType;

    private String path;

    private String perms;

    @JsonProperty("sort_no")
    private Integer sortNo;

    private Integer visible;

    private Integer status;

    private List<PermissionResourceVO> children = new ArrayList<>();

    public static PermissionResourceVO from(SysMenuDO menu) {
        PermissionResourceVO vo = new PermissionResourceVO();
        vo.setId(menu.getId() == null ? null : String.valueOf(menu.getId()));
        vo.setParentId(menu.getParentId() == null ? null : String.valueOf(menu.getParentId()));
        vo.setMenuName(menu.getMenuName());
        vo.setMenuType(typeName(menu.getMenuType()));
        vo.setPath(menu.getPath());
        vo.setPerms(menu.getPerms());
        vo.setSortNo(menu.getSortNo());
        vo.setVisible(menu.getVisible());
        vo.setStatus(menu.getStatus());
        return vo;
    }

    private static String typeName(Integer menuType) {
        if (menuType == null) {
            return null;
        }
        return switch (menuType) {
            case TYPE_DIRECTORY -> "DIRECTORY";
            case TYPE_MENU -> "MENU";
            case TYPE_BUTTON -> "BUTTON";
            default -> null;
        };
    }
}
