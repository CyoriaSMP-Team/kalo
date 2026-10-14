package io.kalo.manager;

import io.kalo.Kalo;
import io.kalo.converter.JavaResourcePackScanner;
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
                .literal("gui")
                .literal("open")
                .permission(Permission.of(PERMISSION_PREFIX + "gui"))
                .required("player", PlayerParser.playerParser())
                .required("gui", NamespacedKeyParser.namespacedKeyParser())
                .handler(ctx -> {
                    Player player = ctx.get("player");
                    NamespacedKey gui = ctx.get("gui");
                    CommandSender sender = ctx.sender().getSender();
                    if (!(Kalo.plugin() instanceof io.kalo.KaloPluginImpl instance)
                            || instance.guiListener() == null) {
                        sender.sendMessage(Component.text("Kalo GUI is not initialized", NamedTextColor.RED));
                        return;
                    }
                    Runnable retired = () -> sender.sendMessage(Component.text(
                            "Player is no longer available", NamedTextColor.RED));
                    if (!player.getScheduler().execute(context.plugin(), () -> {
                        if (instance.guiListener().openGui(player,
                                net.kyori.adventure.key.Key.key(gui.getNamespace(), gui.getKey()))) {
                            sender.sendMessage(Component.text("Opened " + gui
                                    + " for " + player.getName(), NamedTextColor.GREEN));
                        } else {
                            sender.sendMessage(Component.text("Unknown GUI " + gui, NamedTextColor.RED));
                        }
                    }, retired, 1L)) retired.run();
                }));

        manager.command(manager.commandBuilder(Constants.PLUGIN_ID)
                .literal("export")
                .literal("kalopack")
                .permission(Permission.of(PERMISSION_PREFIX + "export"))
                .required("pack", StringParser.stringParser())
                .handler(ctx -> runExportPack(context.plugin(), ctx.sender().getSender(), ctx.get("pack"))));

        manager.command(manager.commandBuilder(Constants.PLUGIN_ID)
                .literal("doctor")
                .permission(Permission.of(PERMISSION_PREFIX + "doctor"))
                .handler(ctx -> runDoctor(ctx.sender().getSender())));

        // Hybrid importer phase 1: safe, read-only scan; native content remains unchanged.
        manager.command(manager.commandBuilder(Constants.PLUGIN_ID)
                .literal("inspect-pack")
                .permission(Permission.of(PERMISSION_PREFIX + "inspect-pack"))
                .required("filename", StringParser.stringParser())
                .handler(ctx -> runInspectPack(context, ctx.sender().getSender(), ctx.get("filename"))));

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

    /**
     * Only KaloPack is exposed for now. The other exporters are still lossy YAML
     * adapters and must not be advertised as completed format converters.
     */
    private static void runExportPack(@NotNull Plugin plugin, @NotNull CommandSender sender,
                                      @NotNull String packName) {
        if (!packName.matches("[a-z0-9_-]{1,64}")) {
            sender.sendMessage(Component.text("Invalid pack name (use lowercase letters, digits, _ or -)",
                    NamedTextColor.RED));
            return;
        }
        java.nio.file.Path root = Constants.dataFolder().toPath().resolve("packs").toAbsolutePath().normalize();
        java.nio.file.Path source = root.resolve(packName);
        if (!Files.isDirectory(source, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            sender.sendMessage(Component.text("Pack does not exist: " + packName, NamedTextColor.RED));
            return;
        }
        java.nio.file.Path output = Constants.dataFolder().toPath()
                .resolve("exports").resolve(packName + ".zip").toAbsolutePath().normalize();
        sender.sendMessage(Component.text("Exporting " + packName + " as a portable KaloPack ZIP...",
                NamedTextColor.YELLOW));

        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                io.kalo.export.ExportReport report = new io.kalo.export.KaloPackExporter().export(source, output);
                plugin.getServer().getGlobalRegionScheduler().execute(plugin, () ->
                        sender.sendMessage(Component.text("Exported " + report.getExportedCount()
                                        + " files to " + output + " (existing exports are never overwritten)",
                                NamedTextColor.GREEN)));
            } catch (Exception e) {
                Plugins.logger().log(Level.WARNING, "KaloPack export failed", e);
                plugin.getServer().getGlobalRegionScheduler().execute(plugin, () ->
                        sender.sendMessage(Component.text("Export failed: " + messageOf(e),
                                NamedTextColor.RED)));
            }
        });
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

    /**
     * Inspect only a local pack in plugins/Kalo/imports. Never accept arbitrary
     * filesystem paths through a game command or replace a running pack here.
     */
    private static void runInspectPack(@NotNull Context context,
                                       @NotNull CommandSender sender,
                                       @NotNull String filename) {
        if (!filename.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,100}\\.zip")
                || filename.contains("..")) {
            sender.sendMessage(Component.text(
                    "Use a .zip filename in plugins/Kalo/imports/ (no directories)",
                    NamedTextColor.RED));
            return;
        }

        java.nio.file.Path source;
        try {
            java.nio.file.Path imports = Constants.dataFolder().toPath().resolve("imports");
            Files.createDirectories(imports);
            source = imports.resolve(filename);
            if (!Files.isRegularFile(source, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    || !source.toRealPath().getParent().equals(imports.toRealPath())) {
                sender.sendMessage(Component.text(
                        "Pack not found in plugins/Kalo/imports/: " + filename, NamedTextColor.RED));
                return;
            }
        } catch (java.io.IOException exception) {
            sender.sendMessage(Component.text(
                    "Could not access pack folder: " + messageOf(exception), NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("Inspecting " + filename
                + " (read-only; nothing will be imported)...", NamedTextColor.YELLOW));
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return JavaResourcePackScanner.scan(source);
            } catch (java.io.IOException exception) {
                throw new java.util.concurrent.CompletionException(exception);
            }
        }).whenComplete((report, failure) -> {
            Runnable reply = () -> {
                if (failure != null) {
                    Plugins.logger().log(Level.WARNING, "Java pack inspection failed", failure);
                    sender.sendMessage(Component.text(
                            "Pack inspection failed: " + messageOf(failure), NamedTextColor.RED));
                    return;
                }
                sender.sendMessage(Component.text("Candidate assets in " + filename + ": "
                        + report.itemModels().size() + " item models, "
                        + report.blockStateCandidates().size() + " blockstates, "
                        + report.texturePaths().size() + " textures, "
                        + report.soundDefinitions().size() + " sound registries, "
                        + report.languageFiles().size() + " language files",
                        NamedTextColor.AQUA));
                sender.sendMessage(Component.text(
                        "Candidates are not verified custom items/blocks; no Geyser mappings were installed.",
                        NamedTextColor.GRAY));
                report.warnings().stream().limit(5).forEach(warning ->
                        sender.sendMessage(Component.text("  Warning: " + warning, NamedTextColor.YELLOW)));
                if (report.warnings().size() > 5) {
                    sender.sendMessage(Component.text(
                            "  ... plus " + (report.warnings().size() - 5) + " more warnings",
                            NamedTextColor.YELLOW));
                }
            };
            if (sender instanceof Player player) {
                player.getScheduler().execute(context.plugin(), reply, () -> {}, 1L);
            } else {
                context.plugin().getServer().getGlobalRegionScheduler().execute(context.plugin(), reply);
            }
        });
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

        // Copy asset directories (textures, models) from source plugin into the pack
        File assetsFolder = new File(packFolder, "assets");
        int assetsCopied = copyAssets(candidates, pluginFolder, assetsFolder, namespace, sender);
        if (assetsCopied > 0) {
            sender.sendMessage(Component.text(
                    "Copied " + assetsCopied + " asset directory(ies) into " + assetsFolder,
                    NamedTextColor.GREEN));
        }

        sender.sendMessage(Component.text(
                "Run /kalo reload to load the imported content.", NamedTextColor.YELLOW));
    }

    /**
     * Copies asset directories (textures, models) from the source plugin into the
     * Kalo pack's assets folder. Each directory is copied under assets/<namespace>/.
     */
    private static int copyAssets(@NotNull List<MigrationDiscovery.Candidate> candidates,
                                  @NotNull File pluginFolder,
                                  @NotNull File assetsFolder,
                                  @NotNull String namespace,
                                  @NotNull CommandSender sender) {
        // Collect unique asset directories from all importers used
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (MigrationDiscovery.Candidate candidate : candidates) {
            for (File dir : candidate.importer().assetDirectories(pluginFolder)) {
                String path = dir.getAbsolutePath();
                if (seen.add(path)) {
                    File dest = new File(assetsFolder, namespace);
                    try {
                        copyDirectory(dir, dest);
                    } catch (Exception e) {
                        Plugins.logger().log(Level.WARNING,
                                "Failed to copy assets from " + dir, e);
                        sender.sendMessage(Component.text(
                                "  Warning: could not copy " + dir.getName() + ": " + e.getMessage(),
                                NamedTextColor.YELLOW));
                    }
                }
            }
        }
        return seen.size();
    }

    /** Recursively copies a directory. */
    private static void copyDirectory(@NotNull File source, @NotNull File dest) throws Exception {
        if (source.isDirectory()) {
            if (!dest.exists()) {
                dest.mkdirs();
            }
            String[] children = source.list();
            if (children != null) {
                for (String child : children) {
                    copyDirectory(new File(source, child), new File(dest, child));
                }
            }
        } else {
            Files.copy(source.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
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
