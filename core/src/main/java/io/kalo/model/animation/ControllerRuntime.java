package io.kalo.model.animation;

import io.kalo.model.ModelIR;
import io.kalo.model.animation.ModelAnimations.Clip;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A Java-side state machine for one Bedrock animation controller.
 *
 * <p>Transitions are evaluated in file order and at most one is taken per tick, matching
 * Bedrock. Conditions use the shared {@link Molang} evaluator: query flags, arrays,
 * arithmetic, comparisons, ternaries, seeded random and argument-aware external queries.
 * Unknown queries read as zero rather than throwing, so a controller authored for Bedrock
 * still loads.</p>
 */
public final class ControllerRuntime {

    public record Signals(
            boolean moving,
            boolean onGround,
            boolean inWater,
            boolean sneaking,
            boolean sprinting,
            boolean jumping,
            double lifeTime,
            double moveSpeed
    ) {
        public static @NotNull Signals still() {
            return new Signals(false, true, false, false, false, false, 0, 0);
        }
    }

    private final ModelIR.Controller controller;
    private final List<Clip> clips;
    private final Molang.QueryResolver queries;
    private final Molang.RandomSource random;
    private final @Nullable Molang.EntityResolver entities;
    private final List<Resolved> resolved = new ArrayList<>();
    private final Map<String, Double> variables = new LinkedHashMap<>();
    private Map<String, Double> lastEnvironment = Map.of();
    private String state;
    private double stateElapsed;

    private ControllerRuntime(ModelIR.Controller controller, List<Clip> clips,
                              Molang.QueryResolver queries, Molang.RandomSource random,
                              @Nullable Molang.EntityResolver entities) {
        this.controller = controller;
        this.clips = List.copyOf(clips);
        this.queries = queries;
        this.random = random;
        this.entities = entities;
        this.state = controller.initialState();
        resolve();
    }

    public static @NotNull ControllerRuntime of(@NotNull ModelIR.Controller controller,
                                               @NotNull List<Clip> clips) {
        return of(controller, clips, (name, arguments) -> 0, null);
    }

    /**
     * @param queries resolves external {@code query.*} functions not present in the built-in
     *                signal environment; unknown names should return zero
     */
    public static @NotNull ControllerRuntime of(@NotNull ModelIR.Controller controller,
                                               @NotNull List<Clip> clips,
                                               @NotNull Molang.QueryResolver queries) {
        return of(controller, clips, queries, null);
    }

    public static @NotNull ControllerRuntime of(@NotNull ModelIR.Controller controller,
                                               @NotNull List<Clip> clips,
                                               @NotNull Molang.QueryResolver queries,
                                               @Nullable Molang.RandomSource random) {
        return of(controller, clips, queries, random, null);
    }

    public static @NotNull ControllerRuntime of(@NotNull ModelIR.Controller controller,
                                               @NotNull List<Clip> clips,
                                               @NotNull Molang.QueryResolver queries,
                                               @Nullable Molang.RandomSource random,
                                               @Nullable Molang.EntityResolver entities) {
        return new ControllerRuntime(controller, clips, queries,
                random != null ? random : Molang.RandomSource.seeded(
                        31L * controller.name().hashCode() + 17L), entities);
    }

    public @NotNull String state() {
        return state;
    }

    public @NotNull List<Clip> activeClips() {
        return resolved.stream().map(Resolved::clip).toList();
    }

    public @NotNull List<Weighted> activeWeightedClips() {
        List<Weighted> weighted = new ArrayList<>();
        for (Resolved entry : resolved) {
            double weight = entry.reference().blendExpression() == null ? 1
                    : Math.max(0, Math.min(1, Molang.evaluate(
                            entry.reference().blendExpression(), lastEnvironment,
                            Map.of(), queries, random, entities)));
            if (weight > 0) {
                weighted.add(new Weighted(entry.clip(), weight));
            }
        }
        return List.copyOf(weighted);
    }

    private void resolve() {
        resolved.clear();
        ModelIR.State current = controller.states().get(state);
        if (current == null) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (ModelIR.AnimationRef reference : current.animations()) {
            Clip clip = ModelAnimations.find(clips, reference.animation());
            if (clip != null && seen.add(clip.name())) {
                resolved.add(new Resolved(clip, reference));
            }
        }
    }

    public void tick(@NotNull Signals signals, double deltaSeconds) {
        stateElapsed += Math.max(0, deltaSeconds);
        ModelIR.State current = controller.states().get(state);
        if (current == null) {
            return;
        }
        Map<String, Double> environment = new LinkedHashMap<>();
        environment.put("query.is_moving", signals.moving() ? 1.0 : 0.0);
        environment.put("query.is_on_ground", signals.onGround() ? 1.0 : 0.0);
        environment.put("query.is_in_water", signals.inWater() ? 1.0 : 0.0);
        environment.put("query.is_sneaking", signals.sneaking() ? 1.0 : 0.0);
        environment.put("query.is_sprinting", signals.sprinting() ? 1.0 : 0.0);
        environment.put("query.is_jumping", signals.jumping() ? 1.0 : 0.0);
        environment.put("query.life_time", signals.lifeTime());
        environment.put("query.anim_time", stateElapsed);
        environment.put("query.modified_move_speed", signals.moveSpeed());
        environment.put("query.ground_speed", signals.moveSpeed());
        environment.put("query.any_animation_finished", anyFinished() ? 1.0 : 0.0);
        environment.put("query.all_animations_finished", allFinished() ? 1.0 : 0.0);
        variables.clear();
        for (Map.Entry<String, ModelIR.Variable> variable
                : current.variables().entrySet()) {
            double input = Molang.evaluate(variable.getValue().input(), environment,
                    Map.of(), queries, random, entities);
            double value = remap(variable.getValue().remapCurve(), input);
            variables.put("variable." + variable.getKey().toLowerCase(Locale.ROOT), value);
            environment.put("variable." + variable.getKey().toLowerCase(Locale.ROOT), value);
        }
        lastEnvironment = Map.copyOf(environment);
        for (ModelIR.Transition transition : current.transitions()) {
            if (Molang.evaluateBoolean(transition.condition(), environment,
                    Map.of(), queries, random, entities)) {
                if (!transition.target().equals(state)
                        && controller.states().containsKey(transition.target())) {
                    state = transition.target();
                    stateElapsed = 0;
                    resolve();
                }
                return;
            }
        }
    }

    private static double remap(Map<Double, Double> curve, double input) {
        if (curve.isEmpty()) {
            return input;
        }
        List<Map.Entry<Double, Double>> points = new ArrayList<>(curve.entrySet());
        if (input <= points.getFirst().getKey()) {
            return points.getFirst().getValue();
        }
        if (input >= points.getLast().getKey()) {
            return points.getLast().getValue();
        }
        for (int index = 1; index < points.size(); index++) {
            Map.Entry<Double, Double> right = points.get(index);
            if (input <= right.getKey()) {
                Map.Entry<Double, Double> left = points.get(index - 1);
                double span = right.getKey() - left.getKey();
                double factor = span == 0 ? 0 : (input - left.getKey()) / span;
                return left.getValue() + (right.getValue() - left.getValue()) * factor;
            }
        }
        return points.getLast().getValue();
    }

    private boolean anyFinished() {
        for (Clip clip : resolved.stream().map(Resolved::clip).toList()) {
            if (clip.finished(stateElapsed)) {
                return true;
            }
        }
        return false;
    }

    private boolean allFinished() {
        if (resolved.isEmpty()) {
            return false;
        }
        for (Clip clip : resolved.stream().map(Resolved::clip).toList()) {
            if (!clip.finished(stateElapsed)) {
                return false;
            }
        }
        return true;
    }

    public record Weighted(@NotNull Clip clip, double weight) {
    }

    private record Resolved(@NotNull Clip clip, @NotNull ModelIR.AnimationRef reference) {
    }

    /** Compatibility shim for callers that only need a boolean. */
    static final class Condition {
        static boolean evaluate(String expression, Map<String, Double> environment) {
            return Molang.evaluateBoolean(expression, environment);
        }
    }
}
