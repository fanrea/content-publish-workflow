package com.contentworkflow.document.application.engine;

import com.contentworkflow.document.application.ingress.DocumentOperationIngressCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class InMemoryDocumentOperationIngressBus implements DocumentOperationIngressBus, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(InMemoryDocumentOperationIngressBus.class);

    private final CollaborationEngine collaborationEngine;
    private final BlockingQueue<DocumentOperationIngressCommand> queue;
    private final Thread worker;

    private volatile boolean running = true;

    public InMemoryDocumentOperationIngressBus(CollaborationEngine collaborationEngine) {
        this(collaborationEngine, 10_000);
    }

    public InMemoryDocumentOperationIngressBus(CollaborationEngine collaborationEngine, int queueCapacity) {
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be > 0");
        }
        this.collaborationEngine = Objects.requireNonNull(collaborationEngine, "collaborationEngine must not be null");
        this.queue = new LinkedBlockingQueue<>(queueCapacity);
        this.worker = new Thread(this::consumeLoop, "doc-ingress-bus-worker");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    @Override
    public void publish(DocumentOperationIngressCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!running) {
            throw new IllegalStateException("in-memory ingress bus is closed");
        }
        boolean offered = queue.offer(command);
        if (!offered) {
            throw new IllegalStateException("in-memory ingress bus queue is full");
        }
    }

    @Override
    public void close() {
        running = false;
        worker.interrupt();
    }

    private void consumeLoop() {
        while (running) {
            try {
                DocumentOperationIngressCommand command = queue.take();
                collaborationEngine.submit(command);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                log.error("in-memory ingress bus submit failed", ex);
            }
        }
    }
}
