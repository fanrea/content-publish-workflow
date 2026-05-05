package com.contentworkflow.document.application.gc;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.OptionalLong;

@Component
@ConditionalOnMissingBean(CompactionWatermarkProvider.class)
public class NoopCompactionWatermarkProvider implements CompactionWatermarkProvider {

    @Override
    public OptionalLong minimumOnlineClock(Long documentId) {
        return OptionalLong.empty();
    }
}
