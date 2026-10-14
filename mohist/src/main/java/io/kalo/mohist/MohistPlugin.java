package io.kalo.mohist;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Spigot 1.20.1-compatible plugin. Does not load Kalo Paper/Folia classes,
 * Forge internals, the Geyser plugin, or server-world mutation code.
 */
public final class MohistPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private final AtomicBoolean busy = new AtomicBoolean();
    private final Set<String> learned = ConcurrentHashMap.newKeySet();
    private Path observations;
    private Path root;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getServer().getBukkitVersion().startsWith("1.20.1-")) {
            getLogger().severe("Kalo-Mohist targets Minecraft 1.20.1; detected "
                    + getServer().getBukkitVersion());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.observations = getDataFolder().toPath().resolve("legacy-observed.properties");
        this.root = getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
        if (!root.resolve("plugins").toFile().isDirectory()) {
            // World containers can be a world subfolder. Bukkit usually exposes
            // the server root via plugins/Kalo-Mohist instead.
            this.root = getDataFolder().toPath().toAbsolutePath().normalize().getParent().getParent();
        }
        try {
            for (var item : ObservedStore.load(observations))
                learned.add(item.material()+":"+item.customModelData());
        } catch (IOException invalid) {
            getLogger().log(Level.SEVERE,
                    "Invalid existing item observations; auto-learning disabled until repaired", invalid);
            getConfig().set("auto-learn", false);
        }
        var command = getCommand("kalomohist");
        if (command == null) {
            getLogger().severe("kalomohist command missing from plugin.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        command.setExecutor(this);
        getServer().getPluginManager().registerEvents(this,this);
        getLogger().info("Kalo Mohist compatibility enabled on "+getServer().getVersion()
                + " / Java "+System.getProperty("java.version")
                + " (legacy 2D export only; Forge items and Geyser login are not bridged)");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("kalomohist.admin")) {
            reply(sender, ChatColor.RED + "No permission");
            return true;
        }
        String action = args.length==0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "status" -> {
                reply(sender, ChatColor.AQUA+"Kalo Mohist | "+getServer().getBukkitVersion()
                        +" | Java "+System.getProperty("java.version"));
                reply(sender, "Auto-learn: "+getConfig().getBoolean("auto-learn",false)
                        +" | saved legacy items: "+learned.size()
                        +" | Geyser/Forge bridge: not installed by this plugin");
            }
            case "learn" -> {
                if (!(sender instanceof Player player)) {
                    reply(sender,ChatColor.RED+"Hold an actual CustomModelData item as a player");
                } else {
                    var held = observed(player.getInventory().getItemInMainHand());
                    if (held==null) reply(sender, ChatColor.YELLOW+"Held item has no legacy CustomModelData");
                    else remember(held,sender);
                }
            }
            case "scan" -> runScan(sender);
            case "export" -> runExport(sender);
            default -> reply(sender,ChatColor.YELLOW+"Usage: /kalomohist <status|scan|learn|export>");
        }
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (getConfig().getBoolean("auto-learn",false))
            Bukkit.getScheduler().runTaskLater(this,()->learnInventory(event.getPlayer()),20);
    }
    @EventHandler
    public void onSwitch(PlayerItemHeldEvent event) {
        if (getConfig().getBoolean("auto-learn",false))
            Bukkit.getScheduler().runTaskLater(this,()->learnInventory(event.getPlayer()),2);
    }
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!getConfig().getBoolean("auto-learn",false)) return;
        for (ItemStack item : event.getInventory().getContents()) learnSilently(item);
        if (event.getPlayer() instanceof Player player) learnInventory(player);
    }

    private void learnInventory(Player player) {
        if (!player.isOnline()) return;
        for (ItemStack item : player.getInventory().getContents()) learnSilently(item);
    }

    private void learnSilently(ItemStack item) {
        var observation = observed(item);
        if (observation != null) remember(observation,null);
    }

    private static LegacyBedrockExporter.Observed observed(ItemStack item) {
        if (item==null || item.getType().isAir() || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta==null || !meta.hasCustomModelData()) return null;
        try {
            return new LegacyBedrockExporter.Observed(
                    item.getType().name().toLowerCase(Locale.ROOT),meta.getCustomModelData());
        } catch (IllegalArgumentException unsupported) {return null;}
    }

    private void remember(LegacyBedrockExporter.Observed item,CommandSender sender) {
        String key = item.material()+":"+item.customModelData();
        if (!learned.add(key)) {
            if (sender!=null) reply(sender,"Already learned "+key);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(this,()->{
            try {
                ObservedStore.remember(observations,item);
                if (sender!=null) reply(sender,ChatColor.GREEN+"Saved "+key+" for export");
            } catch (IOException invalid) {
                learned.remove(key);
                getLogger().log(Level.SEVERE,"Failed to persist CMD "+key,invalid);
                if (sender!=null) reply(sender,ChatColor.RED+"Failed to save item; see log");
            }
        });
    }

    private void runScan(CommandSender sender) {
        if (!busy.compareAndSet(false,true)) {reply(sender,"Scan/export already running");return;}
        Bukkit.getScheduler().runTaskAsynchronously(this,()->{
            try {
                PackSources.Report report=PackSources.discover(root);
                reply(sender,ChatColor.AQUA+"Resource pack candidates: "+report.packs().size()
                        +", Forge mod asset jars: "+report.mods().size());
                for (var candidate:report.packs())
                    reply(sender,"PACK "+root.relativize(candidate.path())
                            +" models="+candidate.models()+" textures="+candidate.textures());
                for (var candidate:report.mods())
                    reply(sender,"MOD  "+root.relativize(candidate.path())
                            +" models="+candidate.models()+" textures="+candidate.textures());
                for (String warning:report.warnings()) reply(sender,ChatColor.YELLOW+"Skipped "+warning);
                if (report.packs().size()>1) reply(sender,ChatColor.YELLOW+
                        "More than one resource pack found. Export will not guess the active one.");
            } catch (IOException invalid) {fail(sender,"Scan",invalid);}
            finally {busy.set(false);}
        });
    }

    private void runExport(CommandSender sender) {
        if (!busy.compareAndSet(false,true)) {reply(sender,"Scan/export already running");return;}
        Bukkit.getScheduler().runTaskAsynchronously(this,()->{
            try {
                PackSources.Report report=PackSources.discover(root);
                if (report.uniquePack()==null) {
                    reply(sender,ChatColor.YELLOW+
                            "Expected exactly one local Java resource pack; run /kalomohist scan");
                    return;
                }
                List<Path> input=new ArrayList<>();
                input.add(report.uniquePack().path());
                // Mod JAR assets are inspected as secondary sources, never installed.
                // Missing collisions and geometry are reported by the converter.
                report.mods().stream().map(PackSources.Source::path).sorted().forEach(input::add);
                var observed=ObservedStore.load(observations);
                if (observed.isEmpty()) {
                    reply(sender,ChatColor.YELLOW+"No observed CMD items yet (auto-learn or /kalomohist learn)");
                    return;
                }
                var result=LegacyBedrockExporter.convert(input,observed);
                if (result.converted()==0) {
                    reply(sender,ChatColor.YELLOW+"No supported flat 2D legacy models converted; output unchanged");
                    for(String skip:result.skipped().stream().limit(8).toList()) reply(sender,skip);
                    return;
                }
                var output=getDataFolder().toPath().resolve("exports");
                LegacyBedrockExporter.write(output,result);
                reply(sender,ChatColor.GREEN+"Generated "+result.converted()+" legacy 2D models at "
                        +output+"; manual Geyser proxy import required");
                if (!result.skipped().isEmpty())
                    reply(sender,ChatColor.YELLOW.toString()+result.skipped().size()+" unsupported items skipped");
            } catch (IOException|RuntimeException invalid) {fail(sender,"Export",invalid);}
            finally {busy.set(false);}
        });
    }

    private void reply(CommandSender sender,String message) {
        if (!isEnabled()) return;
        // Bukkit sendMessage can interact with player implementation; route
        // async completion to the server thread.
        if (Bukkit.isPrimaryThread()) sender.sendMessage(message);
        else Bukkit.getScheduler().runTask(this,()->sender.sendMessage(message));
    }

    private void fail(CommandSender sender,String action,Exception error) {
        getLogger().log(Level.WARNING, action+" failed",error);
        reply(sender,ChatColor.RED+action+" failed: "+error.getMessage());
    }
}
