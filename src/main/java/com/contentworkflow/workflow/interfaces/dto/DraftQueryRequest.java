package com.contentworkflow.workflow.interfaces.dto;

import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 草稿分页查询请求（GET 查询参数绑定）。
 *
 * <p>说明：</p>
 * <p>1) {@code status} 支持多选：/drafts/page?status=DRAFT&status=APPROVED</p>
 * <p>2) 时间参数建议使用 ISO-8601：2026-04-14T12:00:00</p>
 */
public record DraftQueryRequest(
        /**
         * 关键词（模糊匹配 bizNo/title/summary；是否搜索 body 由 {@code searchInBody} 控制）。
         */
        @Size(max = 200)
        String keyword,

        /**
         * 状态过滤（可多选）。
         */
        List<WorkflowStatus> status,

        /**
         * 是否把 body 也纳入关键词搜索（默认 false，避免列表页查询过重）。
         */
        Boolean searchInBody,

        /**
         * 页码（从 1 开始）。
         */
        @Min(1) Integer pageNo,

        /**
         * 每页大小（建议 <= 100）。
         */
        @Min(1) @Max(200) Integer pageSize,

        /**
         * 排序字段（默认 UPDATED_AT）。
         */
        DraftSortBy sortBy,

        /**
         * 排序方向（默认 DESC）。
         */
        SortDirection sortDirection,

        /**
         * 创建时间起（包含）。
         */
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,

        /**
         * 创建时间止（包含）。
         */
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,

        /**
         * 更新时间起（包含）。
         */
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedFrom,

        /**
         * 更新时间止（包含）。
         */
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedTo
) {
    /**
     * 归一化默认值（避免 controller/service 里散落默认值判断）。
     *
     * <p>注意：统计接口会忽略分页参数，但仍建议通过该方法补齐默认值，以统一行为。</p>
     */
    public DraftQueryRequest normalized() {
        Integer normalizedPageNo = pageNo == null ? 1 : pageNo;
        Integer normalizedPageSize = pageSize == null ? 20 : pageSize;
        DraftSortBy normalizedSortBy = sortBy == null ? DraftSortBy.UPDATED_AT : sortBy;
        SortDirection normalizedSortDirection = sortDirection == null ? SortDirection.DESC : sortDirection;
        Boolean normalizedSearchInBody = searchInBody != null && searchInBody;
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        return new DraftQueryRequest(
                normalizedKeyword,
                status,
                normalizedSearchInBody,
                normalizedPageNo,
                normalizedPageSize,
                normalizedSortBy,
                normalizedSortDirection,
                createdFrom,
                createdTo,
                updatedFrom,
                updatedTo
        );
    }

    /**
     * 草稿列表排序字段。
     */
    public enum DraftSortBy {
        ID,
        CREATED_AT,
        UPDATED_AT
    }

    /**
     * 排序方向。
     */
    public enum SortDirection {
        ASC,
        DESC
    }
}
