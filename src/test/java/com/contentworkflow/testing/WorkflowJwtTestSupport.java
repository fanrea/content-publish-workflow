package com.contentworkflow.testing;

import com.contentworkflow.common.security.WorkflowJwtProperties;
import com.contentworkflow.common.security.WorkflowJwtTokenService;
import com.contentworkflow.workflow.domain.enums.WorkflowRole;

import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;

public final class WorkflowJwtTestSupport {

    public static final String TEST_ISSUER = "content-publish-workflow-test";
    public static final String TEST_SECRET = "0123456789abcdef0123456789abcdef";

    private WorkflowJwtTestSupport() {
    }

    public static WorkflowJwtProperties jwtProperties() {
        WorkflowJwtProperties properties = new WorkflowJwtProperties();
        properties.setIssuer(TEST_ISSUER);
        properties.setSecret(TEST_SECRET);
        properties.setClockSkewSeconds(0);
        return properties;
    }

    public static WorkflowJwtTokenService tokenService() {
        return new WorkflowJwtTokenService(jwtProperties());
    }

    public static String bearerToken(String operatorId, String operatorName, WorkflowRole... roles) {
        EnumSet<WorkflowRole> roleSet = roles == null || roles.length == 0
                ? EnumSet.noneOf(WorkflowRole.class)
                : EnumSet.copyOf(Arrays.asList(roles));
        return tokenService().createToken(operatorId, operatorName, roleSet, Duration.ofMinutes(30));
    }
}
