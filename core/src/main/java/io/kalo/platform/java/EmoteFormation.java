package io.kalo.platform.java;

import io.kalo.content.emote.EmoteDefinition;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

/** Places each cast member relative to the lead's position and facing. */
final class EmoteFormation {

    private EmoteFormation() {
    }

    static @NotNull Location at(@NotNull Location lead,
                                @NotNull EmoteDefinition.Performer performer) {
        double yaw = Math.toRadians(lead.getYaw());
        double x = -Math.cos(yaw) * performer.x() - Math.sin(yaw) * performer.z();
        double z = -Math.sin(yaw) * performer.x() + Math.cos(yaw) * performer.z();
        Location result = lead.clone().add(x, performer.y(), z);
        result.setYaw(lead.getYaw());
        result.setPitch(lead.getPitch());
        return result;
    }
}
