package com.contentworkflow.document.interfaces.ws;

import lombok.Data;

/**
 * 协作光标/选区载荷。
 */
@Data
public class DocumentWsCursor {
    /**
     * 选区起点（包含）。
     */
    private Integer start;
    /**
     * 选区终点（不包含）。
     */
    private Integer end;
}

