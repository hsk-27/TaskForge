package com.taskforge.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight, zero-dependency test runner using standard Java reflection.
 *
 * Runs all test methods (public void test*()) across all test classes,
 * measuring execution time and reporting detailed test results.
 */
public class TaskForgeTestRunner {

    public record TestResult(
            String className,
            String methodName,
            boolean passed,
            long elapsedMillis,
            Throwable failure
    ) {}

    public static void main(String[] args) {
        System.out.println("===============================================================");
        System.out.println("         TaskForge Automated Test Suite (Milestone 1)          ");
        System.out.println("===============================================================\n");

        List<Class<?>> testClasses = List.of(
                QueueOrderTest.class,
                QueueCapacityTest.class,
                JobStatusTransitionTest.class,
                SimulatedJobDeterministicTest.class,
                ConcurrentExecutionTest.class,
                ShutdownAndDrainTest.class
        );

        List<TestResult> results = new ArrayList<>();
        long suiteStart = System.currentTimeMillis();

        for (Class<?> clazz : testClasses) {
            System.out.println("Running suite: " + clazz.getSimpleName());
            for (Method method : clazz.getDeclaredMethods()) {
                if (isTestMethod(method)) {
                    TestResult result = runSingleTest(clazz, method);
                    results.add(result);
                    printResult(result);
                }
            }
            System.out.println();
        }

        long suiteDuration = System.currentTimeMillis() - suiteStart;
        printSummary(results, suiteDuration);

        boolean anyFailed = results.stream().anyMatch(r -> !r.passed());
        if (anyFailed) {
            System.exit(1);
        }
    }

    private static boolean isTestMethod(Method method) {
        return Modifier.isPublic(method.getModifiers())
                && !Modifier.isStatic(method.getModifiers())
                && method.getName().startsWith("test")
                && method.getParameterCount() == 0;
    }

    private static TestResult runSingleTest(Class<?> clazz, Method method) {
        long start = System.currentTimeMillis();
        try {
            Object instance = clazz.getDeclaredConstructor().newInstance();
            method.invoke(instance);
            long elapsed = System.currentTimeMillis() - start;
            return new TestResult(clazz.getSimpleName(), method.getName(), true, elapsed, null);
        } catch (InvocationTargetException ite) {
            long elapsed = System.currentTimeMillis() - start;
            return new TestResult(clazz.getSimpleName(), method.getName(), false, elapsed, ite.getCause());
        } catch (Throwable t) {
            long elapsed = System.currentTimeMillis() - start;
            return new TestResult(clazz.getSimpleName(), method.getName(), false, elapsed, t);
        }
    }

    private static void printResult(TestResult result) {
        if (result.passed()) {
            System.out.printf("  [PASS] %-40s (%d ms)%n", result.methodName(), result.elapsedMillis());
        } else {
            System.out.printf("  [FAIL] %-40s (%d ms)%n", result.methodName(), result.elapsedMillis());
            System.err.println("         Error: " + result.failure().getMessage());
            if (result.failure() != null) {
                for (StackTraceElement ste : result.failure().getStackTrace()) {
                    if (ste.getClassName().startsWith("com.taskforge")) {
                        System.err.println("           at " + ste);
                    }
                }
            }
        }
    }

    private static void printSummary(List<TestResult> results, long totalDuration) {
        int total = results.size();
        long passed = results.stream().filter(TestResult::passed).count();
        long failed = total - passed;

        System.out.println("===============================================================");
        System.out.printf("Test Results: %d total, %d passed, %d failed in %d ms%n",
                total, passed, failed, totalDuration);
        System.out.println("===============================================================");

        if (failed == 0) {
            System.out.println("ALL TESTS PASSED SUCCESSFULLY! (Milestone 1 Criteria Met)");
        } else {
            System.out.println("SOME TESTS FAILED! Check stack traces above.");
        }
    }
}
