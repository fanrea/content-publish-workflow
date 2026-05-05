package com.contentworkflow.document.application.gc;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record CompactionPolicyDecision(
        boolean shouldCompact,
        Set<CompactionTrigger> triggers
) {
    public CompactionPolicyDecision {
        Objects.requireNonNull(triggers, "triggers must not be null");
        EnumSet<CompactionTrigger> copied = triggers.isEmpty()
                ? EnumSet.noneOf(CompactionTrigger.class)
                : EnumSet.copyOf(triggers);
        triggers = Collections.unmodifiableSet(copied);
    }

    public static CompactionPolicyDecision noCompact() {
        return new CompactionPolicyDecision(false, EnumSet.noneOf(CompactionTrigger.class));
    }
}
