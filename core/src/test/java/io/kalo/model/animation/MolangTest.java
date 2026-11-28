package io.kalo.model.animation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MolangTest {

    @Test
    void arithmeticAndPrecedenceAreEvaluated() {
        assertEquals(7, Molang.evaluate("1 + 2 * 3", Map.of()), 1e-9);
        assertEquals(9, Molang.evaluate("(1 + 2) * 3", Map.of()), 1e-9);
        assertEquals(2, Molang.evaluate("10 % 4", Map.of()), 1e-9);
        assertEquals(2.5, Molang.evaluate("5 / 2", Map.of()), 1e-9);
        assertEquals(0, Molang.evaluate("5 / 0", Map.of()), 1e-9);
    }

    @Test
    void mathFunctionsProduceNumbers() {
        assertEquals(0, Molang.evaluate("math.sin(0)", Map.of()), 1e-9);
        assertEquals(1, Molang.evaluate("math.cos(0)", Map.of()), 1e-9);
        assertEquals(3, Molang.evaluate("math.sqrt(9)", Map.of()), 1e-9);
        assertEquals(8, Molang.evaluate("math.pow(2, 3)", Map.of()), 1e-9);
        assertEquals(2, Molang.evaluate("math.clamp(5, 1, 2)", Map.of()), 1e-9);
        assertEquals(3, Molang.evaluate("math.lerp(1, 5, 0.5)", Map.of()), 1e-9);
        assertEquals(3.141592653589793, Molang.evaluate("math.pi", Map.of()), 1e-9);
    }

    @Test
    void arraysIndexingAndCountFollowMolangRules() {
        assertEquals(20, Molang.evaluate("[10, 20, 30][1]", Map.of()), 1e-9);
        assertEquals(10, Molang.evaluate("[10, 20, 30][-1]", Map.of()), 1e-9);
        assertEquals(20, Molang.evaluate("[10, 20, 30][4]", Map.of()), 1e-9,
                "large indices wrap by the array size");
        assertEquals(20, Molang.evaluate("[10, 20, 30][1.9]", Map.of()), 1e-9,
                "fractional indices are cast to int");

        Map<String, List<Double>> arrays = Map.of(
                "variable.path", List.of(5.0, 7.0, 9.0));
        assertEquals(7, Molang.evaluate("variable.path[1]", Map.of(), arrays), 1e-9);

        assertEquals(3, Molang.evaluate("query.count([1, 2, 3])", Map.of()), 1e-9);
        assertEquals(4, Molang.evaluate("query.count(9, [1, 2, 3])", Map.of()), 1e-9,
                "query.count counts a scalar as one element");
    }

    @Test
    void deterministicRandomUsesTheProvidedSource() {
        Molang.RandomSource quarter = () -> 0.25;

        assertEquals(3.0, Molang.evaluate("math.random(2, 6)", Map.of(), Map.of(),
                null, quarter), 1e-9);
        assertEquals(3.0, Molang.evaluate("math.random_integer(2, 6)", Map.of(),
                Map.of(), null, quarter), 1e-9);
        assertEquals(2.0, Molang.evaluate("math.random_integer(2, 6)", Map.of(),
                Map.of(), null, () -> 0.0), 1e-9);
        assertEquals(6.0, Molang.evaluate("math.random_integer(2, 6)", Map.of(),
                Map.of(), null, () -> 0.999), 1e-9);

        assertEquals(Molang.evaluate("math.random(0, 1)", Map.of()),
                Molang.evaluate("math.random(0, 1)", Map.of()), 1e-9,
                "the default source is seeded deterministically per expression");
    }

    @Test
    void externalQueryResolverReceivesArguments() {
        Molang.QueryResolver resolver = (name, arguments) -> switch (name) {
            case "query.distance_from_camera" -> 12.5;
            case "query.weighted" -> arguments.get(0) * 2 + arguments.get(1);
            default -> 0;
        };

        assertTrue(Molang.evaluateBoolean("query.distance_from_camera > 10",
                Map.of(), Map.of(), resolver, null));
        assertEquals(7, Molang.evaluate("query.weighted(2, 3)", Map.of(), Map.of(),
                resolver, null), 1e-9);
        assertEquals(7, Molang.evaluate("q.weighted(2, 3)", Map.of(), Map.of(),
                resolver, null), 1e-9);
    }

    @Test
    void multiStatementStructsStringsAndCoalescingWork() {
        assertEquals(9, Molang.evaluate(
                "variable.location.x = 4; variable.location.y = 5;"
                        + " return variable.location.x + variable.location.y;",
                Map.of()), 1e-9);
        assertEquals(7, Molang.evaluate("variable.missing ?? 7", Map.of()), 1e-9);
        assertEquals(1, Molang.evaluate(
                "variable.s = 'ok'; return variable.s == 'ok';", Map.of()), 1e-9);
        assertEquals(0, Molang.evaluate(
                "variable.s = 'ok'; return variable.s != 'ok';", Map.of()), 1e-9);
        assertEquals(3, Molang.evaluate(
                "variable.x = [1, 2, 3];"
                        + " loop(2, { variable.x[0] = variable.x[0] + 1; });"
                        + " return variable.x[0];", Map.of()), 1e-9,
                "the generated controller probe expression evaluates on Java");
    }

    @Test
    void loopsAndForEachMutateArraysAndStructs() {
        assertEquals(3, Molang.evaluate(
                "variable.n = 0; loop(3, { variable.n = variable.n + 1; });"
                        + " return variable.n;", Map.of()), 1e-9);
        assertEquals(6, Molang.evaluate(
                "variable.sum = 0; for_each(variable.item, [1, 2, 3],"
                        + " { variable.sum = variable.sum + variable.item; });"
                        + " return variable.sum;", Map.of()), 1e-9);
        assertEquals(1, Molang.evaluate(
                "variable.n = 0; loop(3, { variable.n = variable.n + 1; break; });"
                        + " return variable.n;", Map.of()), 1e-9);
        assertEquals(2, Molang.evaluate(
                "variable.n = 0; loop(10, { variable.n = variable.n + 1;"
                        + " (variable.n >= 2) ? break; }); return variable.n;",
                Map.of()), 1e-9);
        assertEquals(3, Molang.evaluate(
                "variable.i = 0; variable.n = 0; loop(4, {"
                        + " variable.i = variable.i + 1;"
                        + " (variable.i == 2) ? continue;"
                        + " variable.n = variable.n + 1; }); return variable.n;",
                Map.of()), 1e-9);
    }

    @Test
    void arrowOperatorUsesTheEntityResolver() {
        Molang.EntityResolver resolver = new Molang.EntityResolver() {
            @Override
            public Object entity(String name) {
                return name.equals("variable.pig") ? "pig" : null;
            }

            @Override
            public double resolve(Object entity, String name, List<Double> arguments) {
                return name.equals("query.health") ? 12 : 0;
            }
        };

        assertEquals(12, Molang.evaluate("variable.pig->query.health", Map.of(),
                Map.of(), null, null, resolver), 1e-9);
        assertEquals(12, Molang.evaluate(
                "variable.pig->query.health > 10 ? 12 : 0", Map.of(),
                Map.of(), null, null, resolver), 1e-9);
    }

    @Test
    void variablesComparisonsTernariesAndFlagsWork() {
        Map<String, Double> env = Map.of(
                "query.life_time", 1.5,
                "variable.speed", 0.4);
        assertTrue(Molang.evaluateBoolean("query.life_time > 1 && variable.speed < 1",
                env));
        assertEquals(7, Molang.evaluate("query.life_time > 1 ? 7 : 0", env), 1e-9);
        assertFalse(Molang.evaluateBoolean("query.is_moving", env));
        assertFalse(Molang.evaluateBoolean("unknown_query", env));
        assertEquals(0, Molang.evaluate("this is not valid", env), 1e-9);
    }
}
