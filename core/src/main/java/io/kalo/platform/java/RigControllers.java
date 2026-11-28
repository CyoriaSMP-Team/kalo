package io.kalo.platform.java;

import io.kalo.model.ModelIR;
import io.kalo.model.animation.ControllerRuntime;
import io.kalo.model.animation.ModelAnimations;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Builds and samples the Java state machines that mirror Bedrock animation controllers. */
final class RigControllers {

    private RigControllers() {
    }

    static @NotNull List<ControllerRuntime> create(
            @NotNull List<ModelIR.Controller> controllers,
            @NotNull List<ModelAnimations.Clip> clips) {
        List<ControllerRuntime> runtimes = new ArrayList<>();
        for (ModelIR.Controller controller : controllers) {
            runtimes.add(ControllerRuntime.of(controller, clips));
        }
        return List.copyOf(runtimes);
    }

    /**
     * Ticks every controller and samples the union of their active state animations.
     * Returns an empty pose when no controller selected a clip, so callers can fall back
     * to their ordinary idle loops.
     */
    static @NotNull ModelAnimations.Pose sample(
            @NotNull List<ControllerRuntime> controllers,
            @NotNull ControllerRuntime.Signals signals,
            double deltaSeconds,
            double elapsedSeconds) {
        ModelAnimations.Pose pose = new ModelAnimations.Pose(java.util.Map.of());
        for (ControllerRuntime controller : controllers) {
            controller.tick(signals, deltaSeconds);
            for (ControllerRuntime.Weighted weighted : controller.activeWeightedClips()) {
                pose = pose.blend(weighted.clip().sample(elapsedSeconds),
                        weighted.weight());
            }
        }
        return pose;
    }
}
