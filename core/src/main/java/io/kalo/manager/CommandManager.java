package io.kalo.manager;

import io.kalo.Kalo;
import io.kalo.migration.ImportReport;
import io.kalo.migration.ItemsAdderImporter;
import io.kalo.migration.OraxenImporter;
import io.kalo.utils.Plugins;
import io.kalo.content.item.Item;
import io.kalo.utils.Constants;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.incendo.cloud.bukkit.parser.NamespacedKeyParser;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.permission.Permission;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;

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

        manager.command(manager.commandBuilder(Constants.PLUGIN_ID)
                .literal("import")
                .permission(Permission.of(PERMISSION_PREFIX + "import"))
                .required("file", StringParser.greedyStringParser())
                .handler(ctx -> runImport(ctx.sender().getSender(), ctx.get("file"))));
    }

    /**
     * Converts an Oraxen/Nexo items file into a Kalo pack config.
     *
     * <p>Writes the result next to the source rather than into a pack folder, and never
     * overwrites: an import is something to read through before adopting, not something
     * to drop straight into production.</p>
     */
    private static void runImport(@NotNull CommandSender sender, @NotNull String path) {
        File source = new File(path);
        if (!source.isFile()) {
            sender.sendMessage(Component.text("No such file: " + source, NamedTextColor.RED));
            return;
        }

        ImportReport report = new ImportReport();
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(source);

            // Detected rather than asked for: someone migrating knows which plugin they
            // are leaving, but not necessarily which file format it wrote.
            String converted;
            if (ItemsAdderImporter.looksLikeItemsAdder(config)) {
                sender.sendMessage(Component.text("Detected ItemsAdder format", NamedTextColor.GRAY));
                converted = ItemsAdderImporter.convert(config, report);
            } else {
                sender.sendMessage(Component.text("Detected Oraxen/Nexo format", NamedTextColor.GRAY));
                converted = OraxenImporter.convert(config, "imported", report);
            }

            File destination = new File(source.getParentFile(), source.getName() + ".kalo.yml");
            if (destination.exists()) {
                sender.sendMessage(Component.text(
                        "Refusing to overwrite " + destination.getName() + "; move it aside first",
                        NamedTextColor.RED));
                return;
            }
            Files.writeString(destination.toPath(), converted, StandardCharsets.UTF_8);

            sender.sendMessage(Component.text("Wrote " + destination, NamedTextColor.GREEN));
            report.lines().forEach(line -> sender.sendMessage(Component.text(
                    line, report.hasProblems() ? NamedTextColor.YELLOW : NamedTextColor.GRAY)));
            if (report.hasProblems()) {
                sender.sendMessage(Component.text(
                        "Review the file before using it — some settings did not carry over.",
                        NamedTextColor.YELLOW));
            }
        } catch (Exception e) {
            Plugins.logger().log(Level.WARNING, "Import of " + source + " failed", e);
            sender.sendMessage(Component.text("Import failed: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    @Override
    public void start(@NotNull Context context) {
    }

    @Override
    public void end(@NotNull Context context) {
    }
}
