package com.vaadin.testing.unified.internal.logs;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/** {@code java.util.logging}: a handler on the root logger, for applications that don't log through Logback. */
final class JulHook {
    private JulHook() {}

    static ServerLogs.Hook attach() {
        Logger root = Logger.getLogger("");
        SimpleFormatter formatter = new SimpleFormatter();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    ServerLogs.logged(record.getLevel().intValue() >= Level.SEVERE.intValue(), record.getLoggerName(),
                            formatter.formatMessage(record), record.getThrown());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        handler.setLevel(Level.WARNING);
        root.addHandler(handler);
        return () -> root.removeHandler(handler);
    }
}
