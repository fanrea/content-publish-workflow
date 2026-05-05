package com.contentworkflow.document.application.gc;

import java.util.OptionalLong;

/**
 * Provides online minimum clock watermark for a document.
 */
public interface CompactionWatermarkProvider {

    OptionalLong minimumOnlineClock(Long documentId);
}

