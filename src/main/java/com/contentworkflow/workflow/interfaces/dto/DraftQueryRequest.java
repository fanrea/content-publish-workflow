package com.contentworkflow.workflow.interfaces.dto;

import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 接口层请求模型，用于封装客户端输入参数并承载校验约束。
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
     * 对输入值进行标准化处理，便于后续统一使用。
     *
     * @return 方法处理后的结果对象
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
     * 枚举类型，用于集中定义当前领域中的固定状态、角色或分类值。
     */
    public enum DraftSortBy {
        ID,
        CREATED_AT,
        UPDATED_AT
    }

    /**
     * 枚举类型，用于集中定义当前领域中的固定状态、角色或分类值。
     */
    public enum SortDirection {
        ASC,
        DESC
    }
}
