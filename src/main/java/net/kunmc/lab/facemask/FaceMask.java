package net.kunmc.lab.facemask;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class FaceMask extends JavaPlugin implements TabCompleter, CommandExecutor, Listener {
    private final Map<String, Face> Faces = new HashMap<>();
    private final HashMap<UUID, Face> wearers = new HashMap<>();
    private int CustomModelData;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        saveDefaultConfig();
        FileConfiguration config = null;
        try {
            config = fetchConfig(getConfig().getString("RemoteConfigURL"));
        } catch (Exception e) {
            e.printStackTrace();
            setEnabled(false);
        }
        setFaces(config);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginCommand("facemask").setExecutor(this);
    }

    @Override
    public void onDisable() {
        for (UUID uuid : wearers.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            ItemStack[] armors = p.getInventory().getArmorContents();
            armors[3] = new ItemStack(Material.AIR);
            p.getInventory().setArmorContents(armors);
            wearers.remove(p.getUniqueId());
        }
    }

    public FileConfiguration fetchConfig(String spec) throws IOException, InvalidConfigurationException {
        URL url = new URL(spec);
        InputStreamReader in = new InputStreamReader(url.openStream());
        FileConfiguration config = new YamlConfiguration();
        config.load(in);
        return config;
    }

    public void setFaces(FileConfiguration config) {
        Map<String, String> faceRelations = ((Map<String, String>) config.getMapList("Faces").get(0));
        CustomModelData = config.getInt("CustomModelData");
        for (String key : faceRelations.keySet()) {
            Faces.put(key, new Face(key, Material.valueOf(faceRelations.get(key)), CustomModelData));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length < 1) return false;
        switch (args[0].toLowerCase()) {
            case "set": {
                if (args.length < 3) {
                    sender.sendMessage("Usage: /facemask set <player> <facename>");
                    break;
                }

                List<Player> players = Arrays.stream(Objects.requireNonNull(CommandUtils.getTargets(sender, args[1]))).filter(x -> x instanceof Player).map((x -> (Player) x)).collect(Collectors.toList());
                if (players.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "変更対象は存在しません.");
                    break;
                }

                String facename = args[2].toLowerCase();
                if (Faces.keySet().stream().noneMatch(x -> x.equalsIgnoreCase(facename))) {
                    sender.sendMessage(ChatColor.RED + facename + "は存在しません");
                    break;
                }

                Face face = Faces.get(facename);
                for (Player p : players) {
                    ItemStack[] armors = p.getInventory().getArmorContents();
                    if (armors[3] != null) p.getInventory().addItem(armors[3]);
                    armors[3] = face;
                    p.getInventory().setArmorContents(armors);
                    wearers.put(p.getUniqueId(), face);
                }
                break;
            }
            case "unset": {
                if (args.length < 2) {
                    sender.sendMessage("Usage: /facemask unset <player>");
                    break;
                }

                List<Player> players = Arrays.stream(Objects.requireNonNull(CommandUtils.getTargets(sender, args[1]))).filter(x -> x instanceof Player).map((x -> (Player) x)).collect(Collectors.toList());
                if (players.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "変更対象は存在しません.");
                    break;
                }
                for (Player p : players) {
                    ItemStack[] armors = p.getInventory().getArmorContents();
                    armors[3] = new ItemStack(Material.AIR);
                    p.getInventory().setArmorContents(armors);
                    wearers.remove(p.getUniqueId());
                }
                break;
            }
            case "get": {
                if (args.length < 2) {
                    sender.sendMessage("Usage: /facemask get <player>");
                    break;
                }
                String facename = args[1].toLowerCase();
                if (Faces.keySet().stream().noneMatch(x -> x.equalsIgnoreCase(facename))) {
                    sender.sendMessage(ChatColor.RED + facename + "は存在しません");
                    break;
                }

                ((Player) sender).getInventory().addItem(Faces.get(facename));
            }
            case "reload": {
                FileConfiguration config = null;
                try {
                    config = fetchConfig(getConfig().getString("RemoteConfigURL"));
                } catch (Exception e) {
                    e.printStackTrace();
                    setEnabled(false);
                }
                setFaces(config);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Stream.of("set", "unset", "get", "reload").filter(x -> x.startsWith(args[0])).collect(Collectors.toList());
        }

        if (args.length == 2) {
            switch (args[0]) {
                case "set":
                    return Stream.concat(Bukkit.getOnlinePlayers().stream().map(Player::getName), Stream.of("@a", "@a[distance=..")).filter(x -> x.startsWith(args[1])).collect(Collectors.toList());
                case "unset":
                    return wearers.keySet().stream().map(x -> {
                        Player p = Bukkit.getPlayer(x);
                        if (p == null) {
                            return "";
                        }
                        return p.getName();
                    }).collect(Collectors.toList());
                case "get":
                    return Faces.keySet().stream().filter(x -> x.startsWith(args[1])).collect(Collectors.toList());
                case "reload":
                    return Collections.emptyList();
            }
        }

        if (args.length == 3) {
            switch (args[0]) {
                case "set":
                    return Faces.keySet().stream().filter(x -> x.startsWith(args[2])).collect(Collectors.toList());
            }
        }
        return new ArrayList<>();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (wearers.containsKey(p.getUniqueId())) {
            ItemStack[] armors = p.getInventory().getArmorContents();
            armors[3] = new ItemStack(Material.AIR);
            p.getInventory().setArmorContents(armors);
            e.getDrops().removeIf(x -> x.getType() == wearers.get(p.getUniqueId()).getType());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (wearers.containsKey(p.getUniqueId())) {
            ItemStack[] armors = p.getInventory().getArmorContents();
            armors[3] = new ItemStack(wearers.get(p.getUniqueId()).getType());
            p.getInventory().setArmorContents(armors);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (!(holder instanceof Player)) return;
        Player p = (Player) holder;
        if (wearers.containsKey(p.getUniqueId()) && e.getSlot() == 39) {
            e.setCancelled(true);
            p.closeInventory();
        }
    }
}
