package com.contentworkflow.document.domain.enums;

/**
 * 文档成员角色：
 * OWNER：可编辑、可恢复版本、可管理成员
 * EDITOR：可编辑，不可恢复版本
 * VIEWER：只读，不可编辑
 */
public enum DocumentMemberRole {
    OWNER,
    EDITOR,
    VIEWER;

    /**
     * OWNER/EDITOR 可以执行编辑操作。
     */
    public boolean canEdit() {
        return this == OWNER || this == EDITOR;
    }

    /**
     * 仅 OWNER 可以执行恢复版本。
     */
    public boolean canRestore() {
        return this == OWNER;
    }
}

