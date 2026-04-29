package com.contentworkflow.document.domain.enums;

/**
 * 文档操作类型。
 * INSERT: 在指定位置插入文本；
 * DELETE: 从指定位置删除固定长度；
 * REPLACE: 从指定位置删除固定长度后，再插入新文本。
 */
public enum DocumentOpType {
    INSERT,
    DELETE,
    REPLACE
}
