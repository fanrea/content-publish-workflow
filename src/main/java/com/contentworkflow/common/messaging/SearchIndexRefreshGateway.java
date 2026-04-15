package com.contentworkflow.common.messaging;

import com.contentworkflow.workflow.application.task.PublishTaskEventFactory;

/**
 * Gateway for search index refresh side effects.
 */
public interface SearchIndexRefreshGateway {

    void refresh(PublishTaskEventFactory.SearchIndexRefreshRequestedPayload payload);
}
