package com.vaadin.testing.unified.spring;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedUITest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;
import org.springframework.transaction.annotation.Transactional;

/** Which tests {@link TransactionalCheck} fails, checked by running sample tests through the JUnit test kit. */
class TransactionalCheckTest {

    private static Events run(Class<?> test, String tool) {
        return EngineTestKit.engine("junit-jupiter").configurationParameter("vaadin.testing.tool", tool)
                .selectors(selectClass(test)).execute().allEvents();
    }

    @Test
    void skipsATestWhoseBaseClassIsTransactionalAndRequiresBrowserless() {
        run(ExtendsBrowserlessOnlyBase.class, "playwright")
                .assertStatistics(stats -> stats.skipped(1).failed(0));
    }

    @Test
    void leavesATestThatDoesNotRunOnThisToolToBeSkippedWhicheverConditionComesFirst() {
        run(CheckedFirst.class, "playwright").assertStatistics(stats -> stats.skipped(1).failed(0));
    }

    @Test
    void failsATransactionalTestThatRunsOnThisTool() {
        run(TransactionalOnAnyTool.class, "playwright").assertStatistics(stats -> stats.failed(1));
    }

    // ---------------------------------------------------------------- samples, run only by the tests above

    /** As {@link UnifiedSpringBootTest} registers them, without starting a Spring application. */
    @Transactional
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    @UnifiedUITest
    @ExtendWith(TransactionalCheck.class)
    abstract static class BrowserlessOnlyBase {}

    @Tag("launched-only")
    static class ExtendsBrowserlessOnlyBase extends BrowserlessOnlyBase {
        @Test
        void anything() {}
    }

    @Tag("launched-only")
    @Transactional
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    @ExtendWith(TransactionalCheck.class)
    @UnifiedUITest
    static class CheckedFirst {
        @Test
        void anything() {}
    }

    @Tag("launched-only")
    @Transactional
    @UnifiedUITest
    @ExtendWith(TransactionalCheck.class)
    static class TransactionalOnAnyTool {
        @Test
        void anything() {}
    }
}
