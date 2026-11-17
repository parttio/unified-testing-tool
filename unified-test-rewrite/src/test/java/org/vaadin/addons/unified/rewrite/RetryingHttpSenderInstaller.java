package org.vaadin.addons.unified.rewrite;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.openrewrite.HttpSenderExecutionContextView;
import org.openrewrite.test.RewriteTest;

/**
 * Gives every recipe test's execution context a {@link RetryingHttpSender}, once per test run: registered in
 * {@code META-INF/services}, so no test class has to remember it.
 */
public class RetryingHttpSenderInstaller implements LauncherSessionListener {

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        RewriteTest.defaultExecutionContextCustomizers.put(RetryingHttpSender.class, ctx -> {
            HttpSenderExecutionContextView view = HttpSenderExecutionContextView.view(ctx);
            if (!(view.getHttpSender() instanceof RetryingHttpSender)) {
                view.setHttpSender(new RetryingHttpSender(view.getHttpSender()));
            }
        });
    }
}
