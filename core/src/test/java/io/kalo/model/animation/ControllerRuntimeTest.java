package io.kalo.model.animation;

import io.kalo.model.ModelIR;
import io.kalo.model.animation.ModelAnimations.Clip;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerRuntimeTest {

    private static Clip clip(String name) {
        return new Clip(name, "loop", 1.0, List.of());
    }

    private static ModelIR.Controller controller(String condition) {
        return new ModelIR.Controller("controller.animation.test", "default", Map.of(
                "default", new ModelIR.State(
                        List.of(new ModelIR.AnimationRef("idle", null)),
                        List.of(new ModelIR.Transition("walk", condition)), null),
                "walk", new ModelIR.State(
                        List.of(new ModelIR.AnimationRef("walk", null)),
                        List.of(new ModelIR.Transition("default", "!query.is_moving")), null)));
    }

    private static ControllerRuntime runtime(String condition) {
        return ControllerRuntime.of(controller(condition),
                List.of(clip("idle"), clip("walk")));
    }

    @Test
    void aMovingSignalTransitionsAndBack() {
        ControllerRuntime runtime = runtime("query.is_moving");
        assertEquals("default", runtime.state());
        assertEquals("idle", runtime.activeClips().getFirst().name());

        runtime.tick(new ControllerRuntime.Signals(true, true, false, false,
                false, false, 0, 1), 0.05);
        assertEquals("walk", runtime.state());
        assertEquals("walk", runtime.activeClips().getFirst().name());

        runtime.tick(new ControllerRuntime.Signals(false, true, false, false,
                false, false, 0, 0), 0.05);
        assertEquals("default", runtime.state());
    }

    @Test
    void negationParenthesesAndComparisonsAreEvaluated() {
        assertTrue(ControllerRuntime.Condition.evaluate("!false", Map.of()));
        assertTrue(ControllerRuntime.Condition.evaluate("(true && !false)", Map.of()));
        assertTrue(ControllerRuntime.Condition.evaluate("query.life_time > 0.5",
                Map.of("query.life_time", 1.0)));
        assertFalse(ControllerRuntime.Condition.evaluate("query.life_time > 0.5",
                Map.of("query.life_time", 0.2)));
        assertTrue(ControllerRuntime.Condition.evaluate("query.is_moving || false",
                Map.of("query.is_moving", 1.0)));
        assertFalse(ControllerRuntime.Condition.evaluate("not_a_query", Map.of()));
    }

    @Test
    void stateVariablesFeedRemapCurvesAndAnimationWeights() {
        ModelIR.Controller controlled = new ModelIR.Controller(
                "controller.animation.test", "walk", Map.of(
                "walk", new ModelIR.State(
                        List.of(new ModelIR.AnimationRef("walk", "variable.speed_curve")),
                        List.of(), null,
                        Map.of("speed_curve", new ModelIR.Variable(
                                "query.modified_move_speed",
                                Map.of(0.0, 0.2, 1.0, 0.7))))));
        ControllerRuntime runtime = ControllerRuntime.of(controlled, List.of(clip("walk")));

        runtime.tick(new ControllerRuntime.Signals(false, true, false, false,
                false, false, 0, 0.5), 0.05);
        var weighted = runtime.activeWeightedClips();
        assertEquals(1, weighted.size());
        assertEquals(0.45, weighted.getFirst().weight(), 1e-9);
    }

    @Test
    void arraysRandomAndExternalQueriesDriveTransitions() {
        ModelIR.Controller controlled = new ModelIR.Controller(
                "controller.animation.test", "idle", Map.of(
                "idle", new ModelIR.State(
                        List.of(new ModelIR.AnimationRef("idle", null)),
                        List.of(new ModelIR.Transition("walk",
                                "query.count([1, 2, 3]) == 3"
                                        + " && query.distance_from_camera > 10"
                                        + " && math.random(0.0, 1.0) < 1.0")),
                        null),
                "walk", new ModelIR.State(
                        List.of(new ModelIR.AnimationRef("walk", null)),
                        List.of(), null)));
        ControllerRuntime runtime = ControllerRuntime.of(controlled,
                List.of(clip("idle"), clip("walk")),
                (name, arguments) -> name.equals("query.distance_from_camera") ? 12.0 : 0);

        runtime.tick(new ControllerRuntime.Signals(true, true, false, false,
                false, false, 0, 1), 0.05);

        assertEquals("walk", runtime.state());
    }

    @Test
    void arraysFromQueryFlagsDriveBlendWeights() {
        ModelIR.Controller controlled = new ModelIR.Controller(
                "controller.animation.test", "idle", Map.of(
                "idle", new ModelIR.State(
                        List.of(new ModelIR.AnimationRef("idle",
                                "[0.2, 0.8][query.is_moving]")),
                        List.of(), null)));
        ControllerRuntime runtime = ControllerRuntime.of(controlled,
                List.of(clip("idle")));

        runtime.tick(new ControllerRuntime.Signals(true, true, false, false,
                false, false, 0, 0), 0.05);

        assertEquals(0.8, runtime.activeWeightedClips().getFirst().weight(), 1e-9);
    }

    @Test
    void theArrowOperatorCanDriveATransition() {
        ModelIR.Controller controlled = new ModelIR.Controller(
                "controller.animation.test", "idle", Map.of(
                "idle", new ModelIR.State(
                        List.of(new ModelIR.AnimationRef("idle", null)),
                        List.of(new ModelIR.Transition("walk",
                                "variable.pig->query.health > 10")),
                        null),
                "walk", new ModelIR.State(
                        List.of(new ModelIR.AnimationRef("walk", null)),
                        List.of(), null)));
        Molang.EntityResolver entities = new Molang.EntityResolver() {
            @Override
            public Object entity(String name) {
                return name.equals("variable.pig") ? "pig" : null;
            }

            @Override
            public double resolve(Object entity, String name, List<Double> arguments) {
                return name.equals("query.health") ? 12 : 0;
            }
        };
        ControllerRuntime runtime = ControllerRuntime.of(controlled,
                List.of(clip("idle"), clip("walk")),
                (name, arguments) -> 0, null, entities);

        runtime.tick(ControllerRuntime.Signals.still(), 0.05);

        assertEquals("walk", runtime.state());
    }

    @Test
    void aFinishedAnimationCanDriveTheTransitionBack() {
        ControllerRuntime runtime = ControllerRuntime.of(new ModelIR.Controller(
                "controller.animation.test", "walk", Map.of(
                "walk", new ModelIR.State(
                        List.of(new ModelIR.AnimationRef("walk", null)),
                        List.of(new ModelIR.Transition("default",
                                "query.any_animation_finished")), null),
                "default", new ModelIR.State(List.of(), List.of(), null))),
                List.of(clip("walk")));

        runtime.tick(ControllerRuntime.Signals.still(), 0.5);
        assertEquals("walk", runtime.state());
        runtime.tick(ControllerRuntime.Signals.still(), 0.6);
        assertEquals("default", runtime.state());
    }
}
