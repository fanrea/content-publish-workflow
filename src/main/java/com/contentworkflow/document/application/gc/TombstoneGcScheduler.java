package com.contentworkflow.document.application.gc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TombstoneGcScheduler {

    private static final Logger log = LoggerFactory.getLogger(TombstoneGcScheduler.class);
    private static final String TOMBSTONE_GC_TRIGGER_PREFIX = "TOMBSTONE_GC";

    private final WatermarkGcDecider watermarkGcDecider;
    private final DocumentCompactionTaskPublisher taskPublisher;
    private final Clock clock;
    private final Map<Long, Long> lastPublishedUpperClockByDocument = new ConcurrentHashMap<>();

    public TombstoneGcScheduler(WatermarkGcDecider watermarkGcDecider,
                                DocumentCompactionTaskPublisher taskPublisher) {
        this(watermarkGcDecider, taskPublisher, Clock.systemUTC());
    }

    TombstoneGcScheduler(WatermarkGcDecider watermarkGcDecider,
                         DocumentCompactionTaskPublisher taskPublisher,
                         Clock clock) {
        this.watermarkGcDecider = watermarkGcDecider;
        this.taskPublisher = taskPublisher;
        this.clock = clock;
    }

    public void schedule(Long documentId, long segmentUpperClockInclusive) {
        if (documentId == null || documentId <= 0) {
            log.warn("tombstone gc ignored due to invalid docId, docId={}, upperClock={}",
                    documentId, segmentUpperClockInclusive);
            return;
        }
        if (segmentUpperClockInclusive <= 0L) {
            log.warn("tombstone gc ignored due to invalid upperClock, docId={}, upperClock={}",
                    documentId, segmentUpperClockInclusive);
            return;
        }
        WatermarkGcDecider.GcDecision decision = watermarkGcDecider.decide(documentId, segmentUpperClockInclusive);
        if (!decision.reclaimable()) {
            log.info("tombstone gc skipped, docId={}, upperClock={}, reason={}, minimumOnlineClock={}",
                    documentId,
                    segmentUpperClockInclusive,
                    decision.reason(),
                    decision.minimumOnlineClock());
            return;
        }
        ReserveResult reserveResult = reservePublish(documentId, segmentUpperClockInclusive);
        if (!reserveResult.reserved()) {
            log.debug("tombstone gc deduplicated, docId={}, upperClock={}, reason=idempotent_already_scheduled",
                    documentId, segmentUpperClockInclusive);
            return;
        }
        try {
            taskPublisher.publish(new DocumentCompactionTask(
                    documentId,
                    TOMBSTONE_GC_TRIGGER_PREFIX,
                    Instant.now(clock),
                    segmentUpperClockInclusive
            ));
        } catch (Exception ex) {
            rollbackReserve(documentId, segmentUpperClockInclusive, reserveResult.previousPublished());
            throw ex;
        }
    }

    private ReserveResult reservePublish(Long documentId, long segmentUpperClockInclusive) {
        synchronized (lastPublishedUpperClockByDocument) {
            Long previousPublished = lastPublishedUpperClockByDocument.get(documentId);
            if (previousPublished != null && segmentUpperClockInclusive <= previousPublished) {
                return new ReserveResult(false, previousPublished);
            }
            lastPublishedUpperClockByDocument.put(documentId, segmentUpperClockInclusive);
            return new ReserveResult(true, previousPublished);
        }
    }

    private void rollbackReserve(Long documentId, long segmentUpperClockInclusive, Long previousPublished) {
        synchronized (lastPublishedUpperClockByDocument) {
            Long current = lastPublishedUpperClockByDocument.get(documentId);
            if (!Objects.equals(current, segmentUpperClockInclusive)) {
                return;
            }
            if (previousPublished == null) {
                lastPublishedUpperClockByDocument.remove(documentId);
                return;
            }
            lastPublishedUpperClockByDocument.put(documentId, previousPublished);
        }
    }

    private record ReserveResult(boolean reserved, Long previousPublished) {
    }
}
