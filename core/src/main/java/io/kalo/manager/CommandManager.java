package io.kalo.manager;

import io.kalo.Kalo;
import io.kalo.migration.ImportReport;
import io.kalo.migration.Importer;
import io.kalo.migration.Importers;
import io.kalo.migration.MigrationDiscovery;
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
import org.bukkit.plugin.Plugin;
import org.incendo.cloud.bukkit.parser.NamespacedKeyParser;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.permission.Permission;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
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
                    CommandSender sender = ctx.sender().getSender();
                    sender.sendMessage(Component.text("Reloading...", NamedTextColor.YELLOW));
                    context.plugin().getServer().getGlobalRegionScheduler().execute(context.plugin(), () -> {
                        try {
                            Kalo.plugin().reload();
                            sender.sendMessage(Component.text("Successfully reloaded!", NamedTextColor.GREEN));
                        } catch (RuntimeException e) {
                            Plugins.logger().log(Level.SEVERE, "Kalo reload failed", e);
                            sender.sendMessage(Component.text("Reload failed: " + messageOf(e), NamedTextColor.RED));
                        }
                    });
                }));

        manager.command(manager.commandBuilder(Constants.PLUGIN_ID)
                .literal("doctor")
                .permission(Permission.of(PERMISSION_PREFIX + "doctor"))
                .handler(ctx -> runDoctor(ctx.sender().getSender())));

        manager.command(manager.commandBuilder(Constants.PLUGIN_ID)
                .literal("migrate-world")
                .permission(Permission.of(PERMISSION_PREFIX + "migrate"))
                .handler(ctx -> runMigrateWorldDryRun(ctx.sender().getSender())));

        manager.command(manager.commandBuilder(Constants.PLUGIN_ID)
                .literal("give")
                .permission(Permission.of(PERMISSION_PREFIX + "give"))
                .required("player", PlayerParser.playerParser())
                .required("item", NamespacedKeyParser.namespacedKeyParser())
                .handler(ctx -> {
                    Player player = ctx.get("player");
                    NamespacedKey itemKey = ctx.get("item");

                    org.bukkit.inventory.ItemStack item = itemStack(itemKey);
                    if (item == null) {
                        ctx.sender().getSender().sendMessage(
                                Component.text("Unknown content '" + itemKey + "'", NamedTextColor.RED));
                        return;
                    }

                    CommandSender sender = ctx.sender().getSender();
                    Runnable retired = () -> sender.sendMessage(Component.text(
                            "Could not give the item because " + player.getName() + " is no longer available",
                            NamedTextColor.RED));
                    boolean scheduled = player.getScheduler().execute(context.plugin(), () -> {
                        if (player.getInventory().addItem(item).isEmpty()) {
                            sender.sendMessage(Component.text("Gave " + itemKey + " to " + player.getName(), NamedTextColor.GREEN));
                        } else {
                            sender.sendMessage(Component.text(player.getName() + "'s inventory is full; no item was given", NamedTextColor.RED));
                        }
                    }, retired, 1L);
                    if (!scheduled) retired.run();
                }));

        manager.command(manager.commandBuilder(Constants.PLUGIN_ID)
                .literal("import")
                .permission(Permission.of(PERMISSION_PREFIX + "import"))
                .required("plugin", StringParser.stringParser(),
                        SuggestionProvider.blockingStrings((commandContext, input) ->
                                MigrationDiscovery.installedPluginNames()))
                .handler(ctx -> runImportTarget(ctx.sender().getSender(), ctx.get("plugin"))));

        // The short spelling is a real command branch rather than a parser alias: Paper's
        // Brigadier tree otherwise drops the alias from client-side command completion.
        manager.command(manager.commandBuilder(Constants.PLUGIN_ID)
                .literal("impor")
                .permission(Permission.of(PERMISSION_PREFIX + "import"))
                .required("plugin", StringParser.stringParser(),
                        SuggestionProvider.blockingStrings((commandContext, input) ->
                                MigrationDiscovery.installedPluginNames()))
                .handler(ctx -> runImportTarget(ctx.sender().getSender(), ctx.get("plugin"))));

        // Explicit path form for unusual layouts and files from a plugin that is not
        // currently installed. The normal path is /kalo import <plugin>.
        manager.command(manager.commandBuilder(Constants.PLUGIN_ID)
                .literal("import")
                .literal("file")
                .permission(Permission.of(PERMISSION_PREFIX + "import"))
                .required("path", StringParser.greedyStringParser())
                .handler(ctx -> runImportFile(ctx.sender().getSender(), ctx.get("path"))));

        manager.command(manager.commandBuilder(Constants.PLUGIN_ID)
                .literal("impor")
                .literal("file")
                .permission(Permission.of(PERMISSION_PREFIX + "import"))
                .required("path", StringParser.greedyStringParser())
                .handler(ctx -> runImportFile(ctx.sender().getSender(), ctx.get("path"))));
    }

    private static org.bukkit.inventory.ItemStack itemStack(@NotNull NamespacedKey key) {
        RegistryManager.GlobalRegistries registries = RegistryManager.GlobalRegistries.registries();
        return registries.item().get(key)
                .map(item -> item.itemStack().get())
                .orElseGet(() -> registries.block().get(key)
                        .map(block -> block.itemStack().get())
                        .orElseGet(() -> registries.furniture().get(key)
                                .map(furniture -> furniture.itemStack().get())
                                .orElseGet(() -> registries.armor().get(key)
                                        .map(armor -> armor.itemStack().get())
                                        .orElse(null))));
    }

    private static void runDoctor(@NotNull CommandSender sender) {
        RegistryManager.GlobalRegistries registries = RegistryManager.GlobalRegistries.registries();
        io.kalo.performance.PerformanceSnapshot performance = Kalo.plugin().performance().snapshot();

        sender.sendMessage(Component.text("Kalo diagnostics", NamedTextColor.AQUA));
        sender.sendMessage(Component.text(
                "Runtime: " + performance.pressure()
                        + " | TPS " + String.format(java.util.Locale.ROOT, "%.2f", performance.tps())
                        + " | heap " + String.format(java.util.Locale.ROOT, "%.1f%%", performance.heapUsage() * 100.0),
                performance.pressure() == io.kalo.performance.Pressure.NORMAL
                        ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(
                "Content: " + registries.item().entries().size() + " items, "
                        + registries.block().entries().size() + " blocks, "
                        + registries.furniture().entries().size() + " furniture, "
                        + registries.armor().entries().size() + " armor",
                NamedTextColor.GRAY));

        if (Kalo.plugin() instanceof io.kalo.KaloPluginImpl plugin) {
            sender.sendMessage(Component.text(
                    "Virtual blocks: " + plugin.indexedVirtualBlockCount()
                            + " indexed, " + plugin.renderedVirtualDisplayCount() + " loaded renderers",
                    NamedTextColor.GRAY));
        }

        File generated = new File(Constants.dataFolder(), "generated.zip");
        sender.sendMessage(Component.text(
                "Pack: " + (generated.isFile()
                        ? String.format(java.util.Locale.ROOT, "%.2f MiB", generated.length() / 1048576.0)
                        : "not generated yet")
                        + " | adaptive=" + Kalo.plugin().performance().adaptiveEnabled(),
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "Limits: no artificial content caps; runtime scales until platform/hardware limits.",
                NamedTextColor.DARK_GRAY));
    }

    private static void runMigrateWorldDryRun(@NotNull CommandSender sender) {
        sender.sendMessage(Component.text("Scanning loaded chunks for Kalo-allocated block states...", NamedTextColor.YELLOW));
        if (!(Kalo.plugin() instanceof io.kalo.KaloPluginImpl plugin)) {
            sender.sendMessage(Component.text("Not running as KaloPluginImpl — cannot access allocator", NamedTextColor.RED));
            return;
        }

        io.kalo.migration.WorldMigration.dryRun(plugin, plugin.registryManager().blockStateAllocator())
                .whenComplete((report, error) -> {
                    if (error != null) {
                        Plugins.logger().log(Level.WARNING, "World migration dry-run failed", error);
                        sender.sendMessage(Component.text("Dry-run failed: " + messageOf(error), NamedTextColor.RED));
                        return;
                    }
                    sendMigrationReport(sender, report);
                });
    }

    private static void sendMigrationReport(@NotNull CommandSender sender,
                                            @NotNull io.kalo.migration.WorldMigration.Report report) {
        sender.sendMessage(Component.text("World migration dry-run: " + report.total()
                + " allocated blocks in loaded chunks", NamedTextColor.AQUA));

        report.worlds().values().forEach(world -> {
            String detail = "  " + world.world() + ": " + world.blocks()
                    + " (" + world.chunksScanned() + " chunks)";
            if (!world.complete()) {
                detail += " — " + world.chunksUnreachable() + " chunk(s) could not be read";
            }
            sender.sendMessage(Component.text(detail,
                    world.complete() ? NamedTextColor.GRAY : NamedTextColor.YELLOW));
        });

        if (report.unreadableAssignments() > 0) {
            sender.sendMessage(Component.text(report.unreadableAssignments()
                    + " allocated state(s) could not be parsed by this server and were not"
                    + " searched for; see the console.", NamedTextColor.YELLOW));
        }

        // "Nothing to migrate" and "I could not look" lead to opposite decisions, so an
        // incomplete scan never gets to say the world is clean.
        if (!report.complete()) {
            sender.sendMessage(Component.text(
                    "This scan was incomplete, so the count above is a lower bound — do not"
                            + " read it as nothing to migrate.", NamedTextColor.RED));
            return;
        }

        if (report.total() == 0) {
            sender.sendMessage(Component.text("No allocated blocks found in loaded chunks — nothing to migrate", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(
                    "This is a dry run only. Placed-world migration requires a mapping from your old plugin; see the import report.",
                    NamedTextColor.YELLOW));
        }
    }

    private static void runImportTarget(@NotNull CommandSender sender, @NotNull String target) {
        Plugin plugin = MigrationDiscovery.findInstalled(target);
        if (plugin != null) {
            runImportPlugin(sender, plugin);
            return;
        }

        // Backwards-compatible convenience: a direct path still works when it is not the
        // name of an installed plugin. The explicit /kalo import file form is preferred for
        // paths containing spaces.
        runImportFile(sender, target);
    }

    /** Imports every recognised file from an installed plugin into a Kalo pack. */
    private static void runImportPlugin(@NotNull CommandSender sender, @NotNull Plugin plugin) {
        File pluginFolder = plugin.getDataFolder();
        List<MigrationDiscovery.Candidate> candidates = MigrationDiscovery.scan(pluginFolder);
        if (candidates.isEmpty()) {
            sender.sendMessage(Component.text(
                    "No supported content files found in " + plugin.getName(), NamedTextColor.YELLOW));
            sender.sendMessage(Component.text(
                    "Supported formats: " + Importers.all().stream()
                            .map(Importer::name)
                            .collect(java.util.stream.Collectors.joining(", ")),
                    NamedTextColor.GRAY));
            return;
        }

        String namespace = migrationNamespace(plugin.getName());
        File packFolder = new File(new File(Constants.dataFolder(), "packs"), namespace);
        File configsFolder = new File(packFolder, "configs");
        if ((!configsFolder.exists() && !configsFolder.mkdirs()) || !configsFolder.isDirectory()) {
            sender.sendMessage(Component.text(
                    "Could not create Kalo pack folder " + packFolder, NamedTextColor.RED));
            return;
        }

        try {
            createPackMetadata(packFolder, namespace, plugin.getName());
        } catch (Exception e) {
            Plugins.logger().log(Level.WARNING, "Could not create migration pack " + packFolder, e);
            sender.sendMessage(Component.text(
                    "Could not create Kalo pack metadata: " + e.getMessage(), NamedTextColor.RED));
            return;
        }

        ImportReport report = new ImportReport();
        int written = 0;
        sender.sendMessage(Component.text(
                "Found " + candidates.size() + " supported config file(s) in " + plugin.getName()
                        + " — importing as " + namespace + "...", NamedTextColor.YELLOW));

        for (MigrationDiscovery.Candidate candidate : candidates) {
            try {
                YamlConfiguration source = YamlConfiguration.loadConfiguration(candidate.file());
                String converted = candidate.importer().convert(source, namespace, report);
                File destination = uniqueConfigFile(configsFolder, pluginFolder, candidate.file());
                if (destination == null) {
                    sender.sendMessage(Component.text(
                            "  Skipped existing migration: " + candidate.file().getName(),
                            NamedTextColor.YELLOW));
                    continue;
                }
                Files.writeString(destination.toPath(), converted, StandardCharsets.UTF_8);
                written++;
                sender.sendMessage(Component.text(
                        "  " + candidate.importer().name() + ": "
                                + relative(pluginFolder, candidate.file()) + " -> "
                                + destination.getName(), NamedTextColor.GRAY));
            } catch (Exception e) {
                Plugins.logger().log(Level.WARNING,
                        "Import of " + candidate.file() + " failed", e);
                sender.sendMessage(Component.text(
                        "  Failed: " + candidate.file().getName() + " — " + e.getMessage(),
                        NamedTextColor.RED));
            }
        }

        sender.sendMessage(Component.text(
                "Imported " + written + "/" + candidates.size() + " file(s) into " + packFolder,
                written == candidates.size() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        report.lines().forEach(line -> sender.sendMessage(Component.text(
                line, report.hasProblems() ? NamedTextColor.YELLOW : NamedTextColor.GRAY)));
        sender.sendMessage(Component.text(
                "Copy the source assets into " + new File(packFolder, "assets")
                        + " then run /kalo reload.", NamedTextColor.YELLOW));
    }

    /** Converts one explicitly named source file into a reviewable sidecar file. */
    private static void runImportFile(@NotNull CommandSender sender, @NotNull String path) {
        File source = new File(path);
        if (!source.isFile()) {
            sender.sendMessage(Component.text("No such file: " + source, NamedTextColor.RED));
            return;
        }

        ImportReport report = new ImportReport();
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(source);

            Importer importer = Importers.detect(config);
            if (importer == null) {
                // Better than picking one at random and producing plausible nonsense.
                sender.sendMessage(Component.text(
                        "Could not recognise this file's format. Supported: "
                                + Importers.all().stream().map(Importer::name).collect(java.util.stream.Collectors.joining(", ")),
                        NamedTextColor.RED));
                return;
            }
            sender.sendMessage(Component.text("Detected " + importer.name() + " format", NamedTextColor.GRAY));
            String converted = importer.convert(config, "imported", report);

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

    private static void createPackMetadata(@NotNull File packFolder,
                                           @NotNull String namespace,
                                           @NotNull String author) throws Exception {
        File metadataFile = new File(packFolder, "pack.yml");
        if (metadataFile.exists()) {
            return;
        }

        YamlConfiguration metadata = new YamlConfiguration();
        metadata.set("id", namespace);
        metadata.set("version", "1.0.0");
        metadata.set("author", author);
        metadata.save(metadataFile);
    }

    private static File uniqueConfigFile(@NotNull File configsFolder,
                                         @NotNull File pluginFolder,
                                         @NotNull File source) {
        String relative = relative(pluginFolder, source)
                .replace('\\', '_')
                .replace('/', '_');
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < relative.length(); i++) {
            char character = relative.charAt(i);
            safe.append(Character.isLetterOrDigit(character)
                    || character == '.' || character == '_' || character == '-'
                    ? character : '_');
        }

        String base = safe.toString();
        File destination = new File(configsFolder, base);
        // A second import must not create duplicate definitions beside a hand-reviewed
        // first import. The owner can delete or rename the generated file deliberately if
        // they want to regenerate it.
        return destination.exists() ? null : destination;
    }

    private static @NotNull String relative(@NotNull File root, @NotNull File file) {
        return root.toPath().relativize(file.toPath()).toString();
    }

    private static @NotNull String migrationNamespace(@NotNull String pluginName) {
        StringBuilder namespace = new StringBuilder();
        for (int i = 0; i < pluginName.length(); i++) {
            char character = Character.toLowerCase(pluginName.charAt(i));
            if ((character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')
                    || character == '_' || character == '-' || character == '.') {
                namespace.append(character);
            } else {
                namespace.append('-');
            }
        }
        while (namespace.indexOf("--") >= 0) {
            namespace.deleteCharAt(namespace.indexOf("--"));
        }
        return namespace.isEmpty() ? "imported" : namespace.toString();
    }

    private static @NotNull String messageOf(@NotNull Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    @Override
    public void start(@NotNull Context context) {
    }

    @Override
    public void end(@NotNull Context context) {
    }
}
