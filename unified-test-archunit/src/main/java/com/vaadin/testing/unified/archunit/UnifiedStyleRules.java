package com.vaadin.testing.unified.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.PackageMatcher;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.vaadin.testing.unified.HasEscapeHatch;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedUITest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The Unified UI Test style as ArchUnit rules (spec 12, "Style rules"). Opt-in: add {@code unified-test-archunit}
 * at test scope and check the rules from a plain test:
 *
 * <pre>{@code
 * @Test
 * void followsTheUnifiedStyle() {
 *     UnifiedStyleRules.ALL.check(new ClassFileImporter().importPackages("com.example"));
 * }
 * }</pre>
 *
 * A Unified UI Test is a class annotated, directly or through a meta-annotation, with {@code @UnifiedUITest} or
 * {@code @UnifiedSpringBootTest} (on itself, a superclass or an enclosing class), or a class with a test method
 * that takes a {@link UnifiedPage}. Other classes are not checked. The rules know JUnit, Spring and the Testing
 * Tools by class name only, so none of them has to be on the class path.
 * <p>
 * Each rule allows an empty selection: a code base without Unified UI Tests passes. Wrap a rule in ArchUnit's
 * {@code FreezingArchRule} to adopt it in a suite with existing violations.
 */
public final class UnifiedStyleRules {

    private static final String JUPITER = "org.junit.jupiter.api.";
    private static final String TEST = JUPITER + "Test";
    private static final String TEST_TEMPLATE = JUPITER + "TestTemplate";
    private static final Set<String> TEST_ANNOTATIONS = Set.of(TEST, TEST_TEMPLATE, JUPITER + "RepeatedTest",
            "org.junit.jupiter.params.ParameterizedTest");
    private static final Set<String> LIFECYCLE_ANNOTATIONS = Set.of(JUPITER + "BeforeEach", JUPITER + "AfterEach",
            JUPITER + "BeforeAll", JUPITER + "AfterAll");
    private static final String DISPLAY_NAME = JUPITER + "DisplayName";
    private static final String DISPLAY_NAME_GENERATION = JUPITER + "DisplayNameGeneration";

    private static final String UNIFIED_UI_TEST = UnifiedUITest.class.getName();
    private static final String UNIFIED_SPRING_BOOT_TEST = "com.vaadin.testing.unified.spring.UnifiedSpringBootTest";
    private static final String UNIFIED_PAGE = UnifiedPage.class.getName();
    private static final String HAS_ESCAPE_HATCH = HasEscapeHatch.class.getName();
    private static final String REQUIRES_TESTING_TOOL = RequiresTestingTool.class.getName();

    /** Wait APIs of the Testing Tools: owner class name to the method names that wait. */
    private static final Map<String, Predicate<String>> TOOL_WAITS = Map.of(
            "java.lang.Thread", "sleep"::equals,
            "com.microsoft.playwright.Page", Set.of("waitForTimeout", "waitForSelector")::contains,
            "com.microsoft.playwright.Frame", Set.of("waitForTimeout", "waitForSelector")::contains,
            "com.microsoft.playwright.Locator", "waitFor"::equals,
            "com.vaadin.testbench.TestBenchTestCase", "waitUntil"::equals,
            "com.vaadin.testbench.BrowserTestBase", "waitUntil"::equals,
            "com.vaadin.testbench.ElementQuery", "waitForFirst"::equals,
            "com.vaadin.testing.unified.selenium.TestBenchEscapeHatch",
            name -> name.startsWith("waitUntil") || name.startsWith("waitFor"));
    /** Classes and packages that are a wait whatever is called on them. */
    private static final Set<String> WAIT_CLASSES = Set.of("org.openqa.selenium.support.ui.WebDriverWait",
            "org.openqa.selenium.support.ui.FluentWait");
    private static final PackageMatcher AWAITILITY = PackageMatcher.of("org.awaitility..");

    /** The Testing Tools' own packages, in matching order, with the Testing Tool that has them. */
    private static final List<Map.Entry<PackageMatcher, String>> TOOL_PACKAGES = List.of(
            Map.entry(PackageMatcher.of("com.microsoft.playwright.."), "PLAYWRIGHT"),
            Map.entry(PackageMatcher.of("org.vaadin.addons.dramafinder.."), "PLAYWRIGHT"),
            Map.entry(PackageMatcher.of("com.vaadin.browserless.."), "BROWSERLESS"),
            Map.entry(PackageMatcher.of("com.vaadin.testbench.unit.."), "BROWSERLESS"),
            Map.entry(PackageMatcher.of("com.vaadin.testbench.."), "SELENIUM"),
            Map.entry(PackageMatcher.of("com.vaadin.flow.component..testbench.."), "SELENIUM"),
            Map.entry(PackageMatcher.of("org.openqa.selenium.."), "SELENIUM"));
    /** Flow components: unwrapped on Browserless only. */
    private static final PackageMatcher FLOW_COMPONENTS = PackageMatcher.of("com.vaadin.flow.component..");

    /** Unified UI Tests: the classes the rules check. */
    static final DescribedPredicate<JavaClass> UNIFIED_UI_TESTS =
            DescribedPredicate.describe("are Unified UI Tests", UnifiedStyleRules::isUnifiedUITest);

    /**
     * Every test method of a Unified UI Test reads as a sentence: it has a {@code @DisplayName} with at least two
     * words, or its class (or an enclosing class, for {@code @Nested}) has {@code @DisplayNameGeneration}.
     */
    public static final ArchRule SCENARIO_SENTENCE = classes().that(UNIFIED_UI_TESTS)
            .should(new ArchCondition<JavaClass>("name every test with a scenario sentence") {
                @Override
                public void check(JavaClass testClass, ConditionEvents events) {
                    checkScenarioSentences(testClass, events);
                }
            })
            .as("Scenario sentence: every test in a Unified UI Test reads as a sentence")
            .allowEmptyShould(true);

    /**
     * Unified UI Tests don't wait on their own: no {@code Thread.sleep(..)}, no Testing Tool wait API (Playwright's
     * {@code waitForTimeout}, {@code waitForSelector}, {@code Locator.waitFor}; TestBench's {@code waitUntil},
     * {@code waitForFirst}; Selenium's {@code WebDriverWait} and {@code FluentWait}), no Awaitility. The Unified
     * API waits by itself, and {@code page.waitUntil(..)} covers the rest.
     */
    public static final ArchRule NO_RAW_WAITS = classes().that(UNIFIED_UI_TESTS)
            .should(new ArchCondition<JavaClass>("not wait with Thread.sleep or a Testing Tool's wait API") {
                @Override
                public void check(JavaClass testClass, ConditionEvents events) {
                    checkRawWaits(testClass, events);
                }
            })
            .as("No raw waits: Unified UI Tests wait through the Unified API only")
            .allowEmptyShould(true);

    /**
     * A test that reaches a Testing Tool's own code, directly or through its class's own methods, says so with
     * {@code @RequiresTestingTool} on the test method or its class (or an enclosing class), so other Testing Tools
     * skip it. Testing Tool code is a call to {@code unwrap(..)} or to an {@code ...EscapeHatch.of(..)}, or any use
     * of the Playwright, Dramafinder, TestBench, Selenium or Browserless Test packages. A code unit that calls
     * {@code tryUnwrap(..)} is an optional extra check (spec 08) and may use the Testing Tool types it unwraps.
     * Lifecycle methods ({@code @BeforeEach}, ...) and constructors run for every test, so they need the
     * annotation on the class.
     */
    public static final ArchRule TOOL_CODE_NEEDS_CAPABILITY = classes().that(UNIFIED_UI_TESTS)
            .and().areNotAnonymousClasses().and().areNotLocalClasses()
            .should(new ArchCondition<JavaClass>("declare @RequiresTestingTool where a test reaches Testing Tool code") {
                @Override
                public void check(JavaClass testClass, ConditionEvents events) {
                    checkToolCode(testClass, events);
                }
            })
            .as("Testing Tool code needs its Capability: a test that leaves the Unified API has @RequiresTestingTool")
            .allowEmptyShould(true);

    /** {@link #SCENARIO_SENTENCE}, {@link #NO_RAW_WAITS} and {@link #TOOL_CODE_NEEDS_CAPABILITY}. */
    public static final ArchRule ALL = CompositeArchRule.of(SCENARIO_SENTENCE)
            .and(NO_RAW_WAITS)
            .and(TOOL_CODE_NEEDS_CAPABILITY);

    private UnifiedStyleRules() {}

    // ---------------------------------------------------------------- Unified UI Tests

    static boolean isUnifiedUITest(JavaClass javaClass) {
        return carries(javaClass, UNIFIED_UI_TEST) || carries(javaClass, UNIFIED_SPRING_BOOT_TEST)
                || selfAndEnclosing(javaClass).stream().anyMatch(UnifiedStyleRules::hasUnifiedPageTest);
    }

    private static boolean hasUnifiedPageTest(JavaClass javaClass) {
        return javaClass.getMethods().stream()
                .filter(UnifiedStyleRules::isTestMethod)
                .anyMatch(method -> method.getRawParameterTypes().stream()
                        .anyMatch(type -> type.getName().equals(UNIFIED_PAGE)));
    }

    private static boolean isTestMethod(JavaMethod method) {
        return method.getAnnotations().stream().anyMatch(a -> TEST_ANNOTATIONS.contains(a.getRawType().getName()))
                || method.isMetaAnnotatedWith(TEST) || method.isMetaAnnotatedWith(TEST_TEMPLATE);
    }

    private static boolean isLifecycleMethod(JavaMethod method) {
        return method.getAnnotations().stream()
                .anyMatch(a -> LIFECYCLE_ANNOTATIONS.contains(a.getRawType().getName()));
    }

    /** The annotation is on the class, a superclass or interface, or an enclosing class, directly or as meta. */
    private static boolean carries(JavaClass javaClass, String annotation) {
        return selfAndEnclosing(javaClass).stream()
                .flatMap(c -> Stream.concat(Stream.concat(Stream.of(c), c.getAllRawSuperclasses().stream()),
                        c.getAllRawInterfaces().stream()))
                .anyMatch(c -> annotated(c, annotation));
    }

    private static boolean annotated(CanBeAnnotated element, String annotation) {
        return element.isAnnotatedWith(annotation) || element.isMetaAnnotatedWith(annotation);
    }

    private static List<JavaClass> selfAndEnclosing(JavaClass javaClass) {
        List<JavaClass> classes = new ArrayList<>();
        for (Optional<JavaClass> c = Optional.of(javaClass); c.isPresent(); c = c.get().getEnclosingClass()) {
            classes.add(c.get());
        }
        return classes;
    }

    // ---------------------------------------------------------------- Scenario sentence

    private static void checkScenarioSentences(JavaClass testClass, ConditionEvents events) {
        boolean generated = carries(testClass, DISPLAY_NAME_GENERATION);
        for (JavaMethod method : testClass.getMethods()) {
            if (!isTestMethod(method) || generated) {
                continue;
            }
            Optional<String> displayName = method.tryGetAnnotationOfType(DISPLAY_NAME)
                    .flatMap(a -> a.get("value")).map(String::valueOf);
            if (displayName.isEmpty()) {
                events.add(SimpleConditionEvent.violated(method, "Scenario sentence: test " + describe(method)
                        + " has no @DisplayName; add @DisplayName(\"<what the user does>\") to it, "
                        + "or @DisplayNameGeneration on the class"));
            } else if (!displayName.get().strip().contains(" ")) {
                events.add(SimpleConditionEvent.violated(method, "Scenario sentence: test " + describe(method)
                        + " has @DisplayName(\"" + displayName.get() + "\"), which is no sentence; "
                        + "say what the user does, e.g. @DisplayName(\"Saving a name shows a notification\")"));
            }
        }
    }

    // ---------------------------------------------------------------- No raw waits

    private static void checkRawWaits(JavaClass testClass, ConditionEvents events) {
        Set<String> reported = new HashSet<>();
        for (JavaAccess<?> access : testClass.getAccessesFromSelf()) {
            Optional<String> wait = rawWait(access);
            String message = "No raw waits: " + name(access.getOrigin()) + " waits with " + wait.orElse("") + " "
                    + access.getSourceCodeLocation() + "; let the Unified API wait: assertThat(..) retries until "
                    + "the timeout, page.waitUntil(..) waits for anything else";
            // new WebDriverWait(..).until(..) is one wait
            if (wait.isPresent() && reported.add(message)) {
                events.add(SimpleConditionEvent.violated(access, message));
            }
        }
    }

    private static Optional<String> rawWait(JavaAccess<?> access) {
        JavaClass owner = access.getTargetOwner();
        String name = access.getTarget().getName();
        if (AWAITILITY.matches(owner.getPackageName())) {
            return Optional.of("Awaitility (" + owner.getSimpleName() + "." + name + ")");
        }
        for (JavaClass type : typeHierarchy(owner)) {
            if (WAIT_CLASSES.contains(type.getName())) {
                return Optional.of(type.getSimpleName());
            }
            Predicate<String> waits = TOOL_WAITS.get(type.getName());
            if (access instanceof JavaCall<?> && waits != null && waits.test(name)) {
                return Optional.of(type.getSimpleName() + "." + name + "(..)");
            }
        }
        return Optional.empty();
    }

    private static List<JavaClass> typeHierarchy(JavaClass javaClass) {
        List<JavaClass> types = new ArrayList<>();
        types.add(javaClass);
        types.addAll(javaClass.getAllRawSuperclasses());
        types.addAll(javaClass.getAllRawInterfaces());
        return types;
    }

    // ---------------------------------------------------------------- Testing Tool code needs its Capability

    /** Where a code unit leaves the Unified API, and the Testing Tool that has the code, if known. */
    private record ToolUse(String what, String tool, JavaCodeUnit where, String location) {}

    private static void checkToolCode(JavaClass testClass, ConditionEvents events) {
        boolean classDeclares = carries(testClass, REQUIRES_TESTING_TOOL);
        if (classDeclares) {
            return;
        }
        List<JavaCodeUnit> classWide = new ArrayList<>(testClass.getConstructors());
        testClass.getStaticInitializer().ifPresent(classWide::add);
        for (JavaMethod method : testClass.getMethods()) {
            if (isTestMethod(method)) {
                if (!annotated(method, REQUIRES_TESTING_TOOL)) {
                    reachedToolUse(method, testClass).ifPresent(use -> events.add(SimpleConditionEvent.violated(
                            method, "Testing Tool code needs its Capability: test " + describe(method) + " "
                                    + reached(method, use) + " without @RequiresTestingTool; add "
                                    + annotation(use) + " on the test method or the class, use tryUnwrap(..) "
                                    + "for a check that runs everywhere, or stay in the Unified API")));
                }
            } else if (isLifecycleMethod(method)) {
                classWide.add(method);
            }
        }
        for (JavaCodeUnit codeUnit : classWide) {
            reachedToolUse(codeUnit, testClass).ifPresent(use -> events.add(SimpleConditionEvent.violated(
                    codeUnit, "Testing Tool code needs its Capability: " + describe(codeUnit) + " runs for every "
                            + "test and " + reached(codeUnit, use) + " without @RequiresTestingTool; add "
                            + annotation(use) + " on the class, or stay in the Unified API")));
        }
    }

    private static String reached(JavaCodeUnit start, ToolUse use) {
        String where = use.where().equals(start) ? "" : " in " + name(use.where());
        return use.what() + where + " " + use.location();
    }

    private static String annotation(ToolUse use) {
        return "@RequiresTestingTool(TestingTool." + (use.tool() == null ? "<the one you unwrap for>" : use.tool())
                + ")";
    }

    /** The first Testing Tool code {@code start} reaches through the test class's own code units. */
    private static Optional<ToolUse> reachedToolUse(JavaCodeUnit start, JavaClass testClass) {
        Set<JavaCodeUnit> seen = new HashSet<>();
        Deque<JavaCodeUnit> queue = new ArrayDeque<>(List.of(start));
        while (!queue.isEmpty()) {
            JavaCodeUnit codeUnit = queue.removeFirst();
            if (!seen.add(codeUnit)) {
                continue;
            }
            Optional<ToolUse> use = toolUse(codeUnit);
            if (use.isPresent()) {
                return use;
            }
            Set<JavaAccess<?>> calls = new LinkedHashSet<>(codeUnit.getCallsFromSelf());
            calls.addAll(codeUnit.getCodeUnitReferencesFromSelf());
            for (JavaAccess<?> call : calls) {
                JavaClass owner = call.getTargetOwner();
                if (owner.equals(testClass)) {
                    call.getTarget().resolveMember()
                            .filter(JavaCodeUnit.class::isInstance)
                            .map(JavaCodeUnit.class::cast)
                            .ifPresent(queue::addLast);
                } else if ((owner.isAnonymousClass() || owner.isLocalClass())
                        && selfAndEnclosing(owner).contains(testClass)) {
                    // an anonymous or local class of the test: whatever it declares may run
                    queue.addAll(owner.getCodeUnits());
                }
            }
        }
        return Optional.empty();
    }

    /** Testing Tool code used directly in {@code codeUnit} (including its lambdas). */
    private static Optional<ToolUse> toolUse(JavaCodeUnit codeUnit) {
        boolean optionalCheck = false;
        for (JavaMethodCall call : codeUnit.getMethodCallsFromSelf()) {
            JavaClass owner = call.getTargetOwner();
            String name = call.getTarget().getName();
            if (owner.isAssignableTo(HAS_ESCAPE_HATCH) && name.equals("tryUnwrap")) {
                optionalCheck = true;
            } else if (owner.isAssignableTo(HAS_ESCAPE_HATCH) && name.equals("unwrap")) {
                return Optional.of(new ToolUse("calls the Escape Hatch " + owner.getSimpleName() + ".unwrap(..)",
                        unwrappedTool(codeUnit), codeUnit, call.getSourceCodeLocation().toString()));
            } else if (name.equals("of") && owner.getSimpleName().endsWith("EscapeHatch")) {
                return Optional.of(new ToolUse("enters the Escape Hatch " + owner.getSimpleName() + ".of(..)",
                        toolOfEscapeHatch(owner.getSimpleName()), codeUnit, call.getSourceCodeLocation().toString()));
            }
        }
        if (optionalCheck) {
            return Optional.empty();
        }
        for (JavaClass type : codeUnit.getAllInvolvedRawTypes()) {
            Optional<ToolUse> use = toolType(type, "has", codeUnit, codeUnit.getSourceCodeLocation().toString());
            if (use.isPresent()) {
                return use;
            }
        }
        for (JavaAccess<?> access : codeUnit.getAccessesFromSelf()) {
            String location = access.getSourceCodeLocation().toString();
            Optional<ToolUse> use = toolType(access.getTargetOwner(), "uses", codeUnit, location);
            if (use.isEmpty() && access instanceof JavaFieldAccess fieldAccess) {
                use = toolType(fieldAccess.getTarget().getRawType(), "uses", codeUnit, location);
            }
            if (use.isPresent()) {
                return use;
            }
        }
        for (var classObject : codeUnit.getReferencedClassObjects()) {
            Optional<ToolUse> use = toolType(classObject.getRawType(), "uses", codeUnit,
                    classObject.getSourceCodeLocation().toString());
            if (use.isPresent()) {
                return use;
            }
        }
        for (var check : codeUnit.getInstanceofChecks()) {
            Optional<ToolUse> use = toolType(check.getRawType(), "uses", codeUnit,
                    check.getSourceCodeLocation().toString());
            if (use.isPresent()) {
                return use;
            }
        }
        return Optional.empty();
    }

    private static Optional<ToolUse> toolType(JavaClass type, String verb, JavaCodeUnit codeUnit, String location) {
        JavaClass base = type.isArray() ? type.getBaseComponentType() : type;
        return TOOL_PACKAGES.stream()
                .filter(entry -> entry.getKey().matches(base.getPackageName()))
                .findFirst()
                .map(entry -> new ToolUse(verb + " " + base.getName(), entry.getValue(), codeUnit, location));
    }

    /**
     * The Testing Tool an {@code unwrap(Type.class)} in {@code codeUnit} is for, from the class literals there: a
     * Testing Tool's own type, or a Flow component, which only Browserless has.
     */
    private static String unwrappedTool(JavaCodeUnit codeUnit) {
        String tool = null;
        for (var classObject : codeUnit.getReferencedClassObjects()) {
            JavaClass type = classObject.getRawType();
            Optional<String> own = toolType(type, "", codeUnit, "").map(ToolUse::tool);
            if (own.isPresent()) {
                return own.get();
            }
            if (FLOW_COMPONENTS.matches(type.getPackageName())) {
                tool = "BROWSERLESS";
            }
        }
        return tool;
    }

    private static String toolOfEscapeHatch(String simpleName) {
        if (simpleName.startsWith("Browserless")) {
            return "BROWSERLESS";
        }
        if (simpleName.startsWith("TestBench") || simpleName.startsWith("Selenium")) {
            return "SELENIUM";
        }
        if (simpleName.startsWith("Playwright") || simpleName.startsWith("Dramafinder")) {
            return "PLAYWRIGHT";
        }
        return null;
    }

    // ---------------------------------------------------------------- messages

    /** {@code demo.ProfileViewTest.savesName(UnifiedPage) (ProfileViewTest.java:29)} */
    private static String describe(JavaCodeUnit codeUnit) {
        return name(codeUnit) + " " + codeUnit.getSourceCodeLocation();
    }

    /** {@code demo.ProfileViewTest.savesName(UnifiedPage)} */
    private static String name(JavaCodeUnit codeUnit) {
        StringBuilder parameters = new StringBuilder();
        for (JavaClass type : codeUnit.getRawParameterTypes()) {
            parameters.append(parameters.isEmpty() ? "" : ", ").append(type.getSimpleName());
        }
        String name = codeUnit.isConstructor() ? "<init>" : codeUnit.getName();
        return codeUnit.getOwner().getName() + "." + name + "(" + parameters + ")";
    }
}
