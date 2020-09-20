package net.colonymc.hubcore;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import net.colonymc.api.itemstacks.ItemStackBuilder;
import net.colonymc.api.player.PublicHologram;
import net.colonymc.colonyapi.MainDatabase;
import net.colonymc.hubcore.commands.AboutCommand;
import net.colonymc.hubcore.commands.BuilderModeCommand;
import net.colonymc.hubcore.commands.PluginCommand;
import net.colonymc.hubcore.commands.PvPModeCommand;
import net.colonymc.hubcore.commands.SetupPlayer;
import net.colonymc.hubcore.commands.SpawnCommand;
import net.colonymc.hubcore.fun.battlebox.BattleBox;
import net.colonymc.hubcore.fun.battlebox.BattleBoxCommand;
import net.colonymc.hubcore.fun.battlebox.Fighter;
import net.colonymc.hubcore.fun.commands.CookieCommand;
import net.colonymc.hubcore.fun.commands.KaboomCommand;
import net.colonymc.hubcore.fun.doublejump.DoubleJumpListener;
import net.colonymc.hubcore.fun.pvpmode.PvpMode;
import net.colonymc.hubcore.menus.HelpCommandsMenu;
import net.colonymc.hubcore.menus.HelpfulMenu;
import net.colonymc.hubcore.menus.ServerSelector;
import net.colonymc.hubcore.npcs.LatestDonators;
import net.colonymc.hubcore.npcs.LatestVoters;
import net.colonymc.hubcore.pms.MessageCommand;
import net.colonymc.hubcore.pms.MessageListeners;
import net.colonymc.hubcore.pms.ReplyCommand;
import net.colonymc.hubcore.scoreboard.BattleBoxBoard;
import net.colonymc.hubcore.scoreboard.Scoreboard;
import net.colonymc.hubcore.util.ChatListener;
import net.colonymc.hubcore.util.InteractionListeners;
import net.colonymc.hubcore.util.JoinListener;
import net.colonymc.hubcore.util.LeaveListener;
import net.colonymc.hubcore.util.PortalListener;
import net.colonymc.hubcore.util.items.EnderButtListener;
import net.colonymc.hubcore.util.items.VisibilityListener;
import net.minecraft.server.v1_8_R3.NBTTagString;

public class Main extends JavaPlugin {
	
	static Main instance;
	private static PvpMode pvpInstance = new PvpMode();
	private BattleBox box = new BattleBox();
	private LatestDonators donatorsInstance;
	private LatestVoters votersInstance;
	private File npc = new File(this.getDataFolder(), "npcs.yml");
	private FileConfiguration npcConfig = new YamlConfiguration();
	private ArrayList<PublicHologram> publicHolos = new ArrayList<PublicHologram>();
	boolean started = false;
	
	public void onEnable() {
		instance = this;
		if(MainDatabase.isConnected()) {
			ArrayList<String> pluginNames = new ArrayList<String>();
			for(Plugin pl : Bukkit.getPluginManager().getPlugins()) {
				pluginNames.add(pl.getName());
			}
			if(!pluginNames.contains("ColonySpigotAPI") || !pluginNames.contains("PlaceholderAPI") || !pluginNames.contains("Citizens")) {
				System.out.println(" � The plugin is missing some dependencies! It won't enable!");
				Bukkit.getPluginManager().disablePlugin(Main.this);
				return;
			}
			if(Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
				new Placeholders(this).register();
	        }
			setupConditions();
			setupConfigs();
			startNPCs();
			updateScoreboards();
			initializeCommands();
			initializeListeners();
			started = true;
			System.out.println(" � ColonyHubCore has been sucessfully enabled!");
		}
		else {
			System.out.println(" � ColonyHubCore couldn't connect to the main database!");
		}
	}

	public void onDisable() {
		if(started) {
			ArrayList<String> pluginNames = new ArrayList<String>();
			for(Plugin pl : Bukkit.getPluginManager().getPlugins()) {
				pluginNames.add(pl.getName());
			}
			if(!pluginNames.contains("ColonySpigotAPI") || !pluginNames.contains("PlaceholderAPI") || !pluginNames.contains("Citizens")) {
				System.out.println(" � ColonyHubCore has been sucessfully disabled!");
				return;
			}
			stopNPCs();
			disablePvpPlayers();
			disableBuilderPlayers();
			hidePublicHolograms();
		}
		System.out.println(" � ColonyHubCore has been sucessfully disabled!");
	}
	
	private void setupConditions() {
		Bukkit.getWorld("world").setStorm(false);
		for(Player p : Bukkit.getOnlinePlayers()) {
			p.setFoodLevel(20);
			p.setHealth(20);
			p.setAllowFlight(true);
			p.setScoreboard(new Scoreboard().scoreboardNormalCreate(p));
			p.teleport(new Location(Bukkit.getWorld("world"), 0.5, 110.5, 0.5));
			p.getOpenInventory().getBottomInventory().clear();
			p.getOpenInventory().getTopInventory().clear();
			p.getInventory().clear();
			p.setItemOnCursor(new ItemStack(Material.AIR));
			p.getInventory().setHeldItemSlot(0);
			p.getInventory().setArmorContents(new ItemStack[] {new ItemStack(Material.AIR), new ItemStack(Material.AIR), new ItemStack(Material.AIR), new ItemStack(Material.AIR)});
			p.getInventory().setItem(0, new ItemStackBuilder(Material.NETHER_STAR).name("&5&lServer Selector &7(Right-Click)").addTag("type", new NBTTagString("selector")).build());
			p.getInventory().setItem(2, new ItemStackBuilder(Material.GOLD_AXE).name("&5&lEnable PvP Mode &7(Right-Click)").addTag("type", new NBTTagString("axe")).glint(true).build());
			p.getInventory().setItem(4, new ItemStackBuilder(Material.ENDER_PEARL).name("&5&lEnder Butt &7(Right-Click)").addTag("type", new NBTTagString("pearl")).glint(true).build());
			p.getInventory().setItem(8, new ItemStackBuilder(Material.INK_SACK).name("&5&lPlayer Visibility &7(Right-Click)").addTag("type", new NBTTagString("visibility")).durability((short) 10).build());
		}
	}
	
	private void setupConfigs() {
			try {
				if(!npc.exists()) {
					npc.getParentFile().mkdirs();
					saveResource("npcs.yml", false);
				}
				npcConfig.load(npc);
			} catch (IOException | InvalidConfigurationException e) {
				e.printStackTrace();
			}
	}
	
	private void startNPCs() {
		donatorsInstance = new LatestDonators();
		donatorsInstance.initialize();
		votersInstance = new LatestVoters();
		votersInstance.initialize();
	}
	
	private void updateScoreboards() {
		new BukkitRunnable() {
			@Override
			public void run() {
				for(Player p : Bukkit.getOnlinePlayers()) {
					if(Fighter.getByPlayer(p) == null) {
						Scoreboard.linesUpdate(p);
					}
					else {
						BattleBoxBoard.linesUpdate(p);
					}
				}
			}
		}.runTaskTimerAsynchronously(Main.getInstance(), 0, 1);
	}
	
	private void initializeCommands() {
		this.getCommand("battlebox").setExecutor(new BattleBoxCommand());
		this.getCommand("setupplayer").setExecutor(new SetupPlayer());
		this.getCommand("pvpmode").setExecutor(new PvPModeCommand());
		this.getCommand("buildermode").setExecutor(new BuilderModeCommand());
		this.getCommand("about").setExecutor(new AboutCommand());
		this.getCommand("about").setTabCompleter(new AboutCommand());
		this.getCommand("plugin").setExecutor(new PluginCommand());
		this.getCommand("spawn").setExecutor(new SpawnCommand());
		this.getCommand("message").setExecutor(new MessageCommand());
		this.getCommand("reply").setExecutor(new ReplyCommand());
		this.getCommand("server").setExecutor(new ServerSelector());
		this.getCommand("menu").setExecutor(new HelpfulMenu());
		this.getCommand("help").setExecutor(new HelpCommandsMenu());
		this.getCommand("cookie").setExecutor(new CookieCommand());
		this.getCommand("kaboom").setExecutor(new KaboomCommand());
	}
	
	private void initializeListeners() {
		Bukkit.getPluginManager().registerEvents(box, this);
		Bukkit.getPluginManager().registerEvents(pvpInstance, this);
		Bukkit.getPluginManager().registerEvents(donatorsInstance, this);
		Bukkit.getPluginManager().registerEvents(votersInstance, this);
		Bukkit.getPluginManager().registerEvents(new PortalListener(), this);
		Bukkit.getPluginManager().registerEvents(new PluginCommand(), this);
		Bukkit.getPluginManager().registerEvents(new BuilderModeCommand(), this);
		Bukkit.getPluginManager().registerEvents(new InteractionListeners(), this);
		Bukkit.getPluginManager().registerEvents(new MessageListeners(), this);
		Bukkit.getPluginManager().registerEvents(new JoinListener(), this);
		Bukkit.getPluginManager().registerEvents(new LeaveListener(), this);
		Bukkit.getPluginManager().registerEvents(new ChatListener(), this);
		Bukkit.getPluginManager().registerEvents(new DoubleJumpListener(), this);
		Bukkit.getPluginManager().registerEvents(new EnderButtListener(), this);
		Bukkit.getPluginManager().registerEvents(new VisibilityListener(), this);
		Bukkit.getPluginManager().registerEvents(new ServerSelector(), this);
		Bukkit.getPluginManager().registerEvents(new HelpfulMenu(), this);
		Bukkit.getPluginManager().registerEvents(new HelpCommandsMenu(), this);
		Bukkit.getPluginManager().registerEvents(new Scoreboard(), this);
	}
	
	private void stopNPCs() {
		donatorsInstance.destroy();
		votersInstance.destroy();
	}
	
	private void hidePublicHolograms() {
		for(int i = 0; i < publicHolos.size(); i++) {
			if(i < publicHolos.size()) {
				PublicHologram p = publicHolos.get(0);
				p.destroy();
				publicHolos.remove(p);
			}
		}
	}
	
	private void disablePvpPlayers() {
		for(Player p : Bukkit.getOnlinePlayers()) {
			if(PvpMode.isPvping(p)) {
				pvpInstance.disablePvpMode(p);
			}
		}
	}
	
	private void disableBuilderPlayers() {
		for(int i = 0; i < BuilderModeCommand.builderMode.size(); i++) {
			Player p = BuilderModeCommand.builderMode.get(0);
			BuilderModeCommand.disableBuilder(p);
		}
	}
	
	public static Main getInstance() {
		return instance;
	}
	
	public static PvpMode getPvpInstance() {
		return pvpInstance;
	}
	
	public BattleBox getBox() {
		return box;
	}
	
	public FileConfiguration getNpcConfig() {
		return npcConfig;
	}

}