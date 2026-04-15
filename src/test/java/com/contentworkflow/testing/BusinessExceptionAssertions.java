package com.contentworkflow.testing;

import com.contentworkflow.common.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public final class BusinessExceptionAssertions {

    private BusinessExceptionAssertions() {
    }

    public static void assertCode(BusinessException ex, String expectedCode) {
        assertNotNull(ex, "exception must not be null");
        assertEquals(expectedCode, ex.getCode(), "unexpected BusinessException code");
    }
}

