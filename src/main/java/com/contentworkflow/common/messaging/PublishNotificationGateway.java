package com.contentworkflow.common.messaging;

import com.contentworkflow.workflow.application.task.PublishTaskEventFactory;

/**
 * Gateway for publish notification side effects.
 */
public interface PublishNotificationGateway {

    void notifyPublish(PublishTaskEventFactory.PublishNotificationRequestedPayload payload);
}
