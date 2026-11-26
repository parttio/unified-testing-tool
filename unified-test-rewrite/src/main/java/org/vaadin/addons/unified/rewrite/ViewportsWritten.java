package org.vaadin.addons.unified.rewrite;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;

/**
 * Writes the size a class or test starts at, which an earlier recipe read ({@link Viewports.StartsAt}), as
 * {@code @Viewport}, after the annotations the class or test has by now, and turns any other resize into
 * {@code page.setViewport(..)}: the test then needs a browser, {@code @RequiresBrowser} (ADR 0011, #496).
 */
public class ViewportsWritten extends Recipe {

    @Override
    public String getDisplayName() {
        return "Write the size a test starts at as @Viewport";
    }

    @Override
    public String getDescription() {
        return "Writes the size a class or test starts at, read by an earlier recipe, as `@Viewport`, and turns any other "
                + "resize into `page.setViewport(..)`, on a test that then requires a browser.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            private static final String NEEDS_BROWSER = "unified.viewport.needs-browser";

            @Override
            public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = (J.ClassDeclaration) super.visitClassDeclaration(classDecl, ctx);
                if (getCursor().getMessage(NEEDS_BROWSER) != null) { // a helper resizes: the class's tests need a browser
                    c = requiresBrowser(c);
                }
                var start = c.getMarkers().findFirst(Viewports.StartsAt.class);
                if (start.isEmpty()) {
                    return c;
                }
                c = c.withMarkers(c.getMarkers().removeByType(Viewports.StartsAt.class));
                maybeAddImport(Viewports.VIEWPORT);
                c = Viewports.annotation(start.get().size()).apply(updateCursor(c),
                        c.getCoordinates().addAnnotation((a, b) -> 1));
                return start.get().note() == null ? c : MigrationMarkers.onClass(c, start.get().note());
            }

            @Override
            public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                var markers = MigrationMarkers.collect(getCursor());
                J.MethodDeclaration m = (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);
                java.util.List<String> unplaced = withoutScreenFilling(m, markers);
                m = MigrationMarkers.write(withoutScreenFilling(m), markers);
                for (String note : unplaced) {
                    m = MigrationMarkers.above(m, note);
                }
                boolean needsBrowser = getCursor().getMessage(NEEDS_BROWSER) != null // before updateCursor drops it
                        || m.getMarkers().findFirst(Viewports.NeedsBrowser.class).isPresent();
                m = m.withMarkers(m.getMarkers().removeByType(Viewports.NeedsBrowser.class));
                var start = m.getMarkers().findFirst(Viewports.StartsAt.class);
                if (start.isPresent()) {
                    m = m.withMarkers(m.getMarkers().removeByType(Viewports.StartsAt.class));
                    maybeAddImport(Viewports.VIEWPORT);
                    m = Viewports.annotation(start.get().size()).apply(updateCursor(m),
                            m.getCoordinates().addAnnotation((a, b) -> 1));
                    if (start.get().note() != null) {
                        m = MigrationMarkers.above(m, start.get().note());
                    }
                }
                if (needsBrowser) {
                    if (Helpers.isBrowserTest(m)) {
                        m = requiresBrowser(m);
                    } else {
                        getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, NEEDS_BROWSER, true);
                    }
                }
                return m;
            }

            /**
             * {@code testBench().resizeViewPortTo(w, h)}, {@code window().setSize(new Dimension(w, h))} →
             * {@code page.setViewport(w, h)}, in a method with the page.
             */
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J visited = super.visitMethodInvocation(method, ctx);
                if (!(visited instanceof J.MethodInvocation m)) {
                    return visited;
                }
                J.MethodDeclaration enclosing = getCursor().firstEnclosing(J.MethodDeclaration.class);
                String page = enclosing == null ? null : Pages.parameterName(enclosing);
                String dimension = Viewports.windowDimension(m);
                if (page != null && dimension != null) { // window().getSize().getWidth() → page.viewport().width()
                    getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, NEEDS_BROWSER, true);
                    return JavaTemplate.builder("#{any(" + TestBenchSetupToUnified.UNIFIED_PAGE + ")}.viewport()." + dimension + "()")
                            .javaParser(Stubs.parser())
                            .build()
                            .<J.MethodInvocation>apply(getCursor(), m.getCoordinates().replace(), Stubs.page(page))
                            .withPrefix(m.getPrefix());
                }
                Viewports.Resize resize = Viewports.resize(m);
                if (page == null || resize == null) {
                    return m;
                }
                getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, NEEDS_BROWSER, true);
                if (resize.note(getCursor()) != null) {
                    MigrationMarkers.add(getCursor(), resize.note(getCursor()));
                }
                return JavaTemplate.builder("#{any(" + TestBenchSetupToUnified.UNIFIED_PAGE + ")}.setViewport(#{any(int)}, #{any(int)})")
                        .javaParser(Stubs.parser())
                        .build()
                        .<J.MethodInvocation>apply(getCursor(), m.getCoordinates().replace(), Stubs.page(page),
                                resize.width(), resize.height())
                        .withPrefix(m.getPrefix())
                        .withId(m.getId()); // its Migration Marker is kept by the statement's id
            }

            /**
             * Notes the {@code window().maximize()} and {@code fullscreen()} statements on the statement after each;
             * returns those with none after them.
             */
            private java.util.List<String> withoutScreenFilling(J.MethodDeclaration m,
                    java.util.Map<java.util.UUID, java.util.List<String>> markers) {
                java.util.List<String> pending = new java.util.ArrayList<>();
                if (m.getBody() == null) {
                    return pending;
                }
                for (org.openrewrite.java.tree.Statement st : m.getBody().getStatements()) {
                    String call = Viewports.screenFilling(st);
                    if (call != null) {
                        pending.add(Viewports.screenFillingNote(call));
                    } else if (!pending.isEmpty()) {
                        markers.computeIfAbsent(st.getId(), id -> new java.util.ArrayList<>()).addAll(pending);
                        pending.clear();
                    }
                }
                return pending;
            }

            private J.MethodDeclaration withoutScreenFilling(J.MethodDeclaration m) {
                if (m.getBody() == null) {
                    return m;
                }
                java.util.List<org.openrewrite.java.tree.Statement> kept = new java.util.ArrayList<>();
                org.openrewrite.java.tree.Space prefix = null;
                for (org.openrewrite.java.tree.Statement st : m.getBody().getStatements()) {
                    if (Viewports.screenFilling(st) != null) {
                        prefix = prefix == null ? st.getPrefix() : prefix;
                        continue;
                    }
                    kept.add(prefix == null ? st : st.withPrefix(prefix));
                    prefix = null;
                }
                return kept.size() == m.getBody().getStatements().size() ? m
                        : m.withBody(m.getBody().withStatements(kept));
            }

            /** Playwright's {@code page.viewportSize().width} → {@code page.viewport().width()}, in a method with the page. */
            @Override
            public J visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J visited = super.visitFieldAccess(fieldAccess, ctx);
                J.MethodDeclaration enclosing = getCursor().firstEnclosing(J.MethodDeclaration.class);
                String page = enclosing == null ? null : Pages.parameterName(enclosing);
                if (page == null || !(visited instanceof J.FieldAccess f) || !Viewports.isPlaywrightViewportDimension(f)) {
                    return visited;
                }
                getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, NEEDS_BROWSER, true);
                return JavaTemplate.builder("#{any(" + TestBenchSetupToUnified.UNIFIED_PAGE + ")}.viewport()."
                                + f.getSimpleName() + "()")
                        .javaParser(Stubs.parser())
                        .build()
                        .apply(getCursor(), f.getCoordinates().replace(), Stubs.page(page))
                        .withPrefix(f.getPrefix());
            }

            private <T extends J> T requiresBrowser(T tree) {
                java.util.List<J.Annotation> annotations = tree instanceof J.ClassDeclaration c ? c.getLeadingAnnotations()
                        : ((J.MethodDeclaration) tree).getLeadingAnnotations();
                if (annotations.stream().anyMatch(a -> org.openrewrite.java.tree.TypeUtils.isOfClassType(a.getType(),
                        Viewports.REQUIRES_BROWSER))) {
                    return tree;
                }
                maybeAddImport(Viewports.REQUIRES_BROWSER);
                JavaTemplate template = JavaTemplate.builder("@RequiresBrowser").imports(Viewports.REQUIRES_BROWSER)
                        .javaParser(Stubs.parser()).build();
                return tree instanceof J.ClassDeclaration c
                        ? template.apply(updateCursor(c), c.getCoordinates().addAnnotation((a, b) -> 1))
                        : template.apply(updateCursor((J.MethodDeclaration) tree),
                                ((J.MethodDeclaration) tree).getCoordinates().addAnnotation((a, b) -> 1));
            }
        };
    }
}
