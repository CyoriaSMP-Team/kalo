package io.kalo.manager;

import io.kalo.Kalo;
import io.kalo.content.item.Item;
import io.kalo.utils.Constants;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.incendo.cloud.bukkit.parser.NamespacedKeyParser;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.permission.Permission;
import org.jetbrains.annotations.NotNull;

public final class CommandManager implements Managerial {

    /** Permission nodes were {@code mint.command.*} — a leftover from an earlier project name. */
    private static final String PERMISSION_PREFIX = Constants.PLUGIN_ID + ".command.";

    @Override
    public void preload(@NotNull Context context) {
        PaperCommandManager<CommandSourceStack> manager = PaperCommandManager.builder()
                .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
                .buildOnEnable(context.plugin());

        manager.command(manager.commandBuilder(Constants.PLUGIN_ID)
                .literal("reload")
                .permission(Permission.of(PERMISSION_PREFIX + "reload"))
                .handler(ctx -> {
                    ctx.sender().getSender().sendMessage(Component.text("Reloading...", NamedTextColor.YELLOW));
                    Kalo.plugin().reload();
                    ctx.sender().getSender().sendMessage(Component.text("Successfully reloaded!", NamedTextColor.GREEN));
                }));

        manager.command(manager.commandBuilder(Constants.PLUGIN_ID)
                .literal("give")
                .permission(Permission.of(PERMISSION_PREFIX + "give"))
                .required("player", PlayerParser.playerParser())
                .required("item", NamespacedKeyParser.namespacedKeyParser())
                .handler(ctx -> {
                    Player player = ctx.get("player");
                    NamespacedKey itemKey = ctx.get("item");

                    Item item = RegistryManager.GlobalRegistries.registries().item()
                            .get(itemKey)
                            .orElse(null);
                    if (item == null) {
                        ctx.sender().getSender().sendMessage(
                                Component.text("Unknown item '" + itemKey + "'", NamedTextColor.RED));
                        return;
                    }

                    player.getInventory().addItem(item.itemStack().get());
                }));
    }

    @Override
    public void start(@NotNull Context context) {
    }

    @Override
    public void end(@NotNull Context context) {
    }
}
