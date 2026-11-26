package org.vaadin.addons.unified.rewrite;

import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

/**
 * Viewport and window resizes in the tests being migrated (ADR 0011, #496): the size a test starts at becomes
 * {@code @Viewport}, any later resize {@code page.setViewport(..)}.
 */
final class Viewports {

    static final String VIEWPORT = "com.vaadin.testing.unified.Viewport";
    static final String REQUIRES_BROWSER = "com.vaadin.testing.unified.RequiresBrowser";
    static final String TESTBENCH_COMMANDS = "com.vaadin.testbench.commands.TestBenchCommands";
    static final String PLAYWRIGHT_PAGE = "com.microsoft.playwright.Page";
    static final String SELENIUM_WINDOW = "org.openqa.selenium.WebDriver.Window";
    static final String SELENIUM_DIMENSION = "org.openqa.selenium.Dimension";

    private Viewports() {
    }

    /** A resize to a size in plain numbers, which an annotation can hold. */
    record Size(int width, int height) {
    }

    /**
     * The size a class or test starts at, carried from {@link ViewportsRead}, which reads it while the
     * {@code @BeforeEach} methods are still there, to {@link ViewportsWritten}, which writes it as {@code @Viewport}
     * once the class has its Unified annotation.
     */
    record StartsAt(java.util.UUID id, Size size, String note) implements org.openrewrite.marker.Marker {
        @Override
        public StartsAt withId(java.util.UUID id) {
            return new StartsAt(id, size, note);
        }

        @Override
        public java.util.UUID getId() {
            return id;
        }
    }

    /** {@code tree} marked to start at {@code size}, with the Migration Marker {@code note} or none. */
    static <T extends J> T startsAt(T tree, Size size, String note) {
        return tree.withMarkers(tree.getMarkers().add(new StartsAt(org.openrewrite.Tree.randomId(), size, note)));
    }

    /**
     * A resize: its width and height, and whether it sized the browser's window (Selenium's {@code window().setSize}),
     * which is taller than the page by the browser's own bars, rather than the page.
     */
    record Resize(Expression width, Expression height, boolean window) {
        /** Its size, when both are plain numbers; null otherwise. */
        Size size() {
            Integer w = intLiteral(width);
            Integer h = intLiteral(height);
            return w == null || h == null ? null : new Size(w, h);
        }

        /** The Migration Marker it gets: none for a Viewport resize, which carries over exactly. */
        String note(org.openrewrite.Cursor cursor) {
            return window ? "was a window size: the viewport is now exactly " + width.printTrimmed(cursor) + " × "
                    + height.printTrimmed(cursor) + ", about 143 px taller than before on Chrome" : null;
        }
    }

    /**
     * TestBench's {@code testBench().resizeViewPortTo(w, h)}, Playwright's {@code page.setViewportSize(w, h)} and
     * Selenium's {@code window().setSize(new Dimension(w, h))}; null for any other call.
     */
    static Resize resize(J.MethodInvocation m) {
        if (m.getMethodType() == null || m.getArguments().isEmpty()) {
            return null;
        }
        var declaring = m.getMethodType().getDeclaringType();
        if (m.getSimpleName().equals("resizeViewPortTo") && m.getArguments().size() == 2
                && TypeUtils.isOfClassType(declaring, TESTBENCH_COMMANDS)) {
            return new Resize(m.getArguments().get(0), m.getArguments().get(1), false);
        }
        if (m.getSimpleName().equals("setViewportSize") && m.getArguments().size() == 2
                && TypeUtils.isOfClassType(declaring, PLAYWRIGHT_PAGE)) {
            return new Resize(m.getArguments().get(0), m.getArguments().get(1), false);
        }
        if (m.getSimpleName().equals("setSize") && m.getArguments().size() == 1
                && declaring.getFullyQualifiedName().replace('$', '.').equals(SELENIUM_WINDOW)
                && m.getArguments().get(0) instanceof J.NewClass dimension && dimension.getArguments().size() == 2
                && TypeUtils.isOfClassType(dimension.getType(), SELENIUM_DIMENSION)) {
            return new Resize(dimension.getArguments().get(0), dimension.getArguments().get(1), true);
        }
        return null;
    }

    /**
     * {@code width} or {@code height} for Selenium's {@code window().getSize().getWidth()} or {@code getHeight()}:
     * the Viewport's, now that the test sets that; null for any other call.
     */
    static String windowDimension(J.MethodInvocation m) {
        if (!(m.getSimpleName().equals("getWidth") || m.getSimpleName().equals("getHeight"))
                || !(m.getSelect() instanceof J.MethodInvocation size) || !size.getSimpleName().equals("getSize")
                || size.getMethodType() == null
                || !size.getMethodType().getDeclaringType().getFullyQualifiedName().replace('$', '.').equals(SELENIUM_WINDOW)) {
            return null;
        }
        return m.getSimpleName().equals("getWidth") ? "width" : "height";
    }

    /** Playwright's {@code page.viewportSize().width} or {@code .height}. */
    static boolean isPlaywrightViewportDimension(J.FieldAccess f) {
        return (f.getSimpleName().equals("width") || f.getSimpleName().equals("height"))
                && f.getTarget() instanceof J.MethodInvocation size && size.getSimpleName().equals("viewportSize")
                && size.getMethodType() != null && TypeUtils.isOfClassType(size.getMethodType().getDeclaringType(), PLAYWRIGHT_PAGE);
    }

    /** {@code maximize} or {@code fullscreen} when the statement is Selenium's {@code window().maximize()} or such. */
    static String screenFilling(Statement statement) {
        return statement instanceof J.MethodInvocation m && m.getArguments().stream().allMatch(a -> a instanceof J.Empty)
                && (m.getSimpleName().equals("maximize") || m.getSimpleName().equals("fullscreen"))
                && m.getMethodType() != null
                && m.getMethodType().getDeclaringType().getFullyQualifiedName().replace('$', '.').equals(SELENIUM_WINDOW)
                ? m.getSimpleName() : null;
    }

    /** The Migration Marker of a removed {@code window().maximize()} or {@code fullscreen()}. */
    static String screenFillingNote(String call) {
        return "was window()." + call + "(): a test's browser has no screen to fill; give the test an exact size with"
                + " @Viewport(width = .., height = ..), or page.setViewport(..) during it";
    }

    /** The resize a statement is, to plain numbers; null for anything else. */
    static Resize literalResize(Statement statement) {
        Resize resize = statement instanceof J.MethodInvocation m ? resize(m) : null;
        return resize == null || resize.size() == null ? null : resize;
    }

    private static Integer intLiteral(Expression e) {
        return e instanceof J.Literal l && l.getValue() instanceof Integer i ? i : null;
    }

    /** {@code @Viewport(width = w, height = h)}, for a class or a method. */
    static JavaTemplate annotation(Size size) {
        return JavaTemplate.builder("@Viewport(width = " + size.width() + ", height = " + size.height() + ")")
                .imports(VIEWPORT)
                .javaParser(Stubs.parser("package com.vaadin.testing.unified; public @interface Viewport {"
                        + " int width(); int height(); String name() default \"\"; }"))
                .build();
    }
}
