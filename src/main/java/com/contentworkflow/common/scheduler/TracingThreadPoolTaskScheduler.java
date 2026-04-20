package com.contentworkflow.common.scheduler;

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Callable;

public class TracingThreadPoolTaskScheduler extends ThreadPoolTaskScheduler {

    @Override
    public void execute(Runnable task) {
        super.execute(wrap(task));
    }

    @Override
    public java.util.concurrent.Future<?> submit(Runnable task) {
        return super.submit(wrap(task));
    }

    @Override
    public <T> java.util.concurrent.Future<T> submit(Callable<T> task) {
        return super.submit(wrap(task));
    }

    @Override
    public org.springframework.util.concurrent.ListenableFuture<?> submitListenable(Runnable task) {
        return super.submitListenable(wrap(task));
    }

    @Override
    public <T> org.springframework.util.concurrent.ListenableFuture<T> submitListenable(Callable<T> task) {
        return super.submitListenable(wrap(task));
    }

    @Override
    public java.util.concurrent.ScheduledFuture<?> schedule(Runnable task,
                                                            org.springframework.scheduling.Trigger trigger) {
        return super.schedule(wrap(task), trigger);
    }

    @Override
    public java.util.concurrent.ScheduledFuture<?> schedule(Runnable task, java.time.Instant startTime) {
        return super.schedule(wrap(task), startTime);
    }

    @Override
    public java.util.concurrent.ScheduledFuture<?> scheduleAtFixedRate(Runnable task,
                                                                       java.time.Instant startTime,
                                                                       java.time.Duration period) {
        return super.scheduleAtFixedRate(wrap(task), startTime, period);
    }

    @Override
    public java.util.concurrent.ScheduledFuture<?> scheduleAtFixedRate(Runnable task,
                                                                       java.time.Duration period) {
        return super.scheduleAtFixedRate(wrap(task), period);
    }

    @Override
    public java.util.concurrent.ScheduledFuture<?> scheduleWithFixedDelay(Runnable task,
                                                                          java.time.Instant startTime,
                                                                          java.time.Duration delay) {
        return super.scheduleWithFixedDelay(wrap(task), startTime, delay);
    }

    @Override
    public java.util.concurrent.ScheduledFuture<?> scheduleWithFixedDelay(Runnable task,
                                                                          java.time.Duration delay) {
        return super.scheduleWithFixedDelay(wrap(task), delay);
    }

    private Runnable wrap(Runnable task) {
        String triggerName = task == null ? "scheduled-task" : task.getClass().getSimpleName();
        return WorkflowSchedulerTraceContext.wrapScheduled(triggerName, task);
    }

    private <T> Callable<T> wrap(Callable<T> task) {
        String triggerName = task == null ? "scheduled-callable" : task.getClass().getSimpleName();
        return WorkflowSchedulerTraceContext.wrapScheduled(triggerName, task);
    }
}
