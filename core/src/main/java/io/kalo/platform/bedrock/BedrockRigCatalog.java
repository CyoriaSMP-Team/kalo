package io.kalo.platform.bedrock;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/** Runtime lookup from a vehicle/emote key to the Bedrock client-entity identifier. */
public final class BedrockRigCatalog {

    private BedrockRigCatalog() {
    }

    public static @Nullable String identifierFor(@NotNull Key contentKey) {
        return BedrockRegistrationSnapshot.await(Duration.ZERO)
                .flatMap(snapshot -> snapshot.rigs().stream()
                        .filter(rig -> rig.contentKey().equals(contentKey))
                        .findFirst())
                .map(BedrockRigRegistration::entityIdentifier)
                .orElse(null);
    }
}
