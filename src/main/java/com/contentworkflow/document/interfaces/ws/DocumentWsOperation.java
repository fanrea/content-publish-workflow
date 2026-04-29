package com.contentworkflow.document.interfaces.ws;

import com.contentworkflow.document.domain.enums.DocumentOpType;
import lombok.Data;

/**
 * 操作级编辑载荷。
 */
@Data
public class DocumentWsOperation {
    /**
     * 操作类型：INSERT / DELETE / REPLACE。
     */
    private DocumentOpType opType;
    /**
     * 操作起始位置（基于当前文档内容的字符偏移）。
     */
    private Integer position;
    /**
     * 删除长度；INSERT 时可为 0。
     */
    private Integer length;
    /**
     * 插入文本；DELETE 时可为空。
     */
    private String text;
}
