package com.vaadin.testing.unified.internal.logs;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;

/** Logback, when it is the SLF4J backend: an appender on the root logger. Loaded only when Logback is there. */
final class LogbackHook {
    private LogbackHook() {}

    static boolean available() {
        try {
            Class<?> context = Class.forName("ch.qos.logback.classic.LoggerContext", false,
                    LogbackHook.class.getClassLoader());
            return context.isInstance(org.slf4j.LoggerFactory.getILoggerFactory());
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    static ServerLogs.Hook attach() {
        LoggerContext context = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent event) {
                if (event.getLevel().isGreaterOrEqual(Level.WARN)) {
                    IThrowableProxy thrown = event.getThrowableProxy();
                    ServerLogs.logged(event.getLevel().isGreaterOrEqual(Level.ERROR), event.getLoggerName(),
                            event.getFormattedMessage(), thrown == null ? null : thrown.getClassName(),
                            thrown == null ? null : thrown.getMessage());
                }
            }
        };
        appender.setName("unified-ui-tests");
        appender.setContext(context);
        appender.start();
        root.addAppender(appender);
        return () -> {
            root.detachAppender(appender);
            appender.stop();
        };
    }
}
