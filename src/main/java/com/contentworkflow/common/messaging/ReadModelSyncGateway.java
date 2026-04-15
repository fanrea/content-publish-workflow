package com.contentworkflow.common.messaging;

import com.contentworkflow.workflow.application.task.PublishTaskEventFactory;

/**
 * Gateway for downstream read-model sync side effects.
 */
public interface ReadModelSyncGateway {

    void sync(PublishTaskEventFactory.ReadModelSyncRequestedPayload payload);
}
