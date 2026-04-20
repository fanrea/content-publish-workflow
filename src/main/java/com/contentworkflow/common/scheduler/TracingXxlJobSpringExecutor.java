package com.contentworkflow.common.scheduler;

import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.handler.impl.MethodJobHandler;

import java.lang.reflect.Method;

public class TracingXxlJobSpringExecutor extends XxlJobSpringExecutor {

    @Override
    protected void registJobHandler(XxlJob xxlJob, Object bean, Method executeMethod) {
        if (xxlJob == null) {
            return;
        }

        String name = xxlJob.value();
        Class<?> beanClass = bean.getClass();
        String executeMethodName = executeMethod.getName();

        if (name.trim().isEmpty()) {
            throw new RuntimeException("xxl-job method-jobhandler name invalid, bean[" + beanClass + "#" + executeMethodName + "].");
        }
        if (XxlJobExecutor.loadJobHandler(name) != null) {
            throw new RuntimeException("xxl-job jobhandler[" + name + "] naming conflicts.");
        }

        executeMethod.setAccessible(true);
        Method initMethod = resolveLifecycleMethod(beanClass, executeMethodName, xxlJob.init(), "init");
        Method destroyMethod = resolveLifecycleMethod(beanClass, executeMethodName, xxlJob.destroy(), "destroy");

        IJobHandler delegate = new MethodJobHandler(bean, executeMethod, initMethod, destroyMethod);
        XxlJobExecutor.registJobHandler(name, new TracingXxlJobHandler(name, delegate));
    }

    private Method resolveLifecycleMethod(Class<?> beanClass,
                                          String executeMethodName,
                                          String lifecycleMethodName,
                                          String phase) {
        if (lifecycleMethodName == null || lifecycleMethodName.trim().isEmpty()) {
            return null;
        }
        try {
            Method lifecycleMethod = beanClass.getDeclaredMethod(lifecycleMethodName);
            lifecycleMethod.setAccessible(true);
            return lifecycleMethod;
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(
                    "xxl-job method-jobhandler " + phase + " method invalid, bean[" +
                            beanClass + "#" + executeMethodName + "]."
            );
        }
    }
}
