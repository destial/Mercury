package tc.oc.pgm.listeners;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Lists;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.format.TextColor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.vehicle.VehicleUpdateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.util.Vector;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.Permissions;
import tc.oc.pgm.api.community.vanish.VanishManager;
import tc.oc.pgm.api.event.BlockTransformEvent;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchManager;
import tc.oc.pgm.api.match.event.MatchFinishEvent;
import tc.oc.pgm.api.match.event.MatchLoadEvent;
import tc.oc.pgm.api.match.event.MatchStartEvent;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.setting.SettingKey;
import tc.oc.pgm.api.setting.SettingValue;
import tc.oc.pgm.events.MapPoolAdjustEvent;
import tc.oc.pgm.events.PlayerParticipationStopEvent;
import tc.oc.pgm.gamerules.GameRulesMatchModule;
import tc.oc.pgm.modules.TimeLockModule;
import tc.oc.pgm.util.UsernameFormatUtils;
import tc.oc.pgm.util.text.PeriodFormats;
import tc.oc.pgm.util.text.TextTranslations;

public class PGMListener implements Listener, PluginMessageListener {
  private static final String DO_DAYLIGHT_CYCLE = "doDaylightCycle";

  private final Plugin parent;
  private final MatchManager mm;
  private final VanishManager vm;

  // Single-write, multi-read lock used to create the first match
  private final ReentrantReadWriteLock lock;

  public PGMListener(Plugin parent, MatchManager mm, VanishManager vm) {
    this.parent = parent;
    this.mm = mm;
    this.vm = vm;
    lock = new ReentrantReadWriteLock();
    parent.getServer().getMessenger().registerIncomingPluginChannel(parent, "BungeeCord", this);
    parent.getServer().getMessenger().registerOutgoingPluginChannel(parent, "BungeeCord");

    Bukkit.getScheduler()
        .runTaskTimer(
            parent,
            () -> {
              joinDelay.values().removeIf(l -> l <= System.currentTimeMillis());
            },
            0L,
            5L);
  }

  @EventHandler(ignoreCancelled = true)
  public void onPrePlayerLogin(final AsyncPlayerPreLoginEvent event) {
    if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED
        || mm.getMatches().hasNext()) return;

    // Create the match when the first player joins
    if (lock.writeLock().tryLock()) {
      // If the server is suspended, need to release so match can be created
      final Server server = parent.getServer();
      if (server.isSuspended()) {
        server.setSuspended(false);
      }

      try {
        mm.createMatch(null).get();
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      } finally {
        lock.writeLock().unlock();
      }
    }

    // If a match is being created, wait until its done
    try {
      lock.readLock().tryLock(15, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      lock.readLock().unlock();
    }

    if (!mm.getMatches().hasNext()) {
      event.disallow(
          AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
          TextTranslations.translate("misc.incorrectWorld", null));
    }
  }

  private final ConcurrentHashMap<UUID, Long> joinDelay = new ConcurrentHashMap<>();

  @EventHandler
  public void onPlayerLogin(final PlayerLoginEvent event) {
    // allow premiums to join when the server is full
    PGM.get().getDatastore().getPlayerData(event.getPlayer().getUniqueId());
    PGM.get().getDatastore().getCoins(event.getPlayer().getUniqueId());
    PGM.get().getDatastore().getStats(event.getPlayer().getUniqueId());
    if (event.getResult() == Result.KICK_FULL) {
      if (event.getPlayer().hasPermission(Permissions.JOIN_FULL)) {
        event.allow();
      } else {
        event.setKickMessage(TextTranslations.translate("misc.serverFull", event.getPlayer()));
      }
    } else if (event.getResult() == Result.ALLOWED) {
      if (joinDelay.containsKey(event.getPlayer().getUniqueId())) {
        long millis = joinDelay.get(event.getPlayer().getUniqueId());
        int seconds = (int) ((millis - System.currentTimeMillis()) / 1000);
        event.disallow(
            Result.KICK_OTHER,
            ChatColor.RED + "You have to wait " + seconds + "s before rejoining!");
      }
    }
  }

  @EventHandler(priority = EventPriority.LOW)
  public void addPlayerOnJoin(final PlayerJoinEvent event) {
    Match match = mm.getMatch(event.getWorld());
    if (match == null) {
      event
          .getPlayer()
          .kickPlayer(
              ChatColor.RED + TextTranslations.translate("misc.incorrectWorld", event.getPlayer()));
      this.parent
          .getLogger()
          .info(
              "Had to kick player "
                  + event.getPlayer().getName()
                  + " due to them spawning in the incorrect world");
      return;
    }

    match.addPlayer(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void broadcastJoinMessage(final PlayerJoinEvent event) {
    // Handle join message and send it to all players except the one joining
    Match match = mm.getMatch(event.getWorld());
    if (match == null) return;

    if (event.getJoinMessage() != null) {
      event.setJoinMessage(null);
      MatchPlayer player = match.getPlayer(event.getPlayer());
      if (player != null) {
        // Announce actual staff join
        announceJoinOrLeave(player, true, vm.isVanished(player.getId()));
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void removePlayerOnDisconnect(PlayerQuitEvent event) {
    Match match = this.mm.getMatch(event.getWorld());
    if (match == null) return;

    if (event.getQuitMessage() != null) {
      MatchPlayer player = match.getPlayer(event.getPlayer());
      if (player != null) {
        // Announce actual staff quit
        announceJoinOrLeave(player, false, vm.isVanished(player.getId()));
      }
      event.setQuitMessage(null);
    }

    joinDelay.put(event.getPlayer().getUniqueId(), System.currentTimeMillis() + (1000 * 10));
    match.removePlayer(event.getPlayer());
  }

  public static void announceJoinOrLeave(MatchPlayer player, boolean join, boolean staffOnly) {
    announceJoinOrLeave(player, join, staffOnly, false);
  }

  public static void announceJoinOrLeave(
      MatchPlayer player, boolean join, boolean staffOnly, boolean force) {
    checkNotNull(player);
    Collection<MatchPlayer> viewers =
        player.getMatch().getPlayers().stream()
            .filter(p -> (!staffOnly || p.getBukkit().hasPermission(Permissions.STAFF)))
            .collect(Collectors.toList());

    for (MatchPlayer viewer : viewers) {
      if (player.equals(viewer)) continue;
      if (!staffOnly && player.isVanished() && viewer.getBukkit().hasPermission(Permissions.STAFF))
        continue; // Skip staff during fake broadcast

      final String key =
          (join ? "misc.join" : "misc.leave")
              + (staffOnly && (player.isVanished() || force) ? ".quiet" : "");

      SettingValue option = viewer.getSettings().getValue(SettingKey.JOIN);
      if (option.equals(SettingValue.JOIN_ON)) {
        String nameStr =
            PGM.get()
                .getNameDecorationRegistry()
                .getDecoratedName(player.getBukkit(), player.getParty());
        Component component =
            TranslatableComponent.of(key, TextColor.YELLOW, TextComponent.of(nameStr));
        viewer.sendMessage(
            staffOnly
                ? ChatDispatcher.ADMIN_CHAT_PREFIX.append(component.color(TextColor.YELLOW))
                : component.color(TextColor.YELLOW));
      }
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void protect36(final PlayerInteractEvent event) {
    if (event.getClickedBlock() != null) {
      if (event.getClickedBlock().getType() == Material.PISTON_MOVING_PIECE) {
        event.setCancelled(true);
      }
    }
  }

  // sometimes arrows stuck in players persist through deaths
  @EventHandler
  public void fixStuckArrows(final PlayerRespawnEvent event) {
    event.getPlayer().setArrowsStuck(0);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void clearActiveEnderPearls(final PlayerDeathEvent event) {
    Collection<EnderPearl> pearls =
        event.getEntity().getWorld().getEntitiesByClass(EnderPearl.class);
    for (EnderPearl entity : pearls) {
      if (entity.getShooter() == event.getEntity()) {
        entity.remove();
      }
    }
  }

  // fix item pickup to work the way it should
  @EventHandler(priority = EventPriority.HIGHEST)
  public void handleItemPickup(final PlayerPickupItemEvent event) {
    Player nearestPlayer = event.getPlayer();
    double closestDistance =
        event.getItem().getLocation().distance(event.getPlayer().getLocation());

    for (Entity nearEntity : event.getItem().getNearbyEntities(1.5, 1.5, 1.5)) {
      double distance = event.getItem().getLocation().distanceSquared(nearEntity.getLocation());

      if (nearEntity instanceof Player && distance < closestDistance) {
        nearestPlayer = (Player) nearEntity;
        closestDistance = distance;
      }
    }

    if (nearestPlayer != event.getPlayer()) event.setCancelled(true);
  }

  //
  // Time Lock
  // lock time before, during (if time lock enabled), and after the match
  //
  @EventHandler
  public void lockTime(final MatchLoadEvent event) {
    event.getMatch().getWorld().setGameRuleValue(DO_DAYLIGHT_CYCLE, Boolean.toString(false));
  }

  @EventHandler
  public void unlockTime(final MatchStartEvent event) {
    boolean unlockTime = false;
    if (!event.getMatch().getModule(TimeLockModule.class).isTimeLocked()) {
      unlockTime = true;
    }

    GameRulesMatchModule gameRulesModule = event.getMatch().getModule(GameRulesMatchModule.class);
    if (gameRulesModule != null && gameRulesModule.getGameRules().containsKey(DO_DAYLIGHT_CYCLE)) {
      unlockTime = Boolean.parseBoolean(gameRulesModule.getGameRules().get(DO_DAYLIGHT_CYCLE));
    }

    event.getMatch().getWorld().setGameRuleValue(DO_DAYLIGHT_CYCLE, Boolean.toString(unlockTime));
  }

  @EventHandler
  public void lockTime(final MatchFinishEvent event) {
    event.getMatch().getWorld().setGameRuleValue(DO_DAYLIGHT_CYCLE, Boolean.toString(false));
  }

  @EventHandler
  public void freezeWorld(final BlockTransformEvent event) {
    Match match = this.mm.getMatch(event.getWorld());
    if (match == null || match.isFinished()) event.setCancelled(true);
  }

  @EventHandler
  public void freezeVehicle(final VehicleUpdateEvent event) {
    Match match = this.mm.getMatch(event.getWorld());
    if (match == null || match.isFinished()) {
      event.getVehicle().setVelocity(new Vector());
    }
  }

  @EventHandler
  public void nerfFishing(PlayerFishEvent event) {
    if (event.getCaught() instanceof Item) {
      Item caught = (Item) event.getCaught();
      if (caught.getItemStack().getType() != Material.RAW_FISH) {
        caught.setItemStack(new ItemStack(Material.RAW_FISH));
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void dropItemsOnQuit(PlayerParticipationStopEvent event) {
    MatchPlayer quitter = event.getPlayer();
    if (!quitter.isAlive()) return;

    for (ItemStack item : quitter.getInventory().getContents()) {
      if (item == null || item.getType() == Material.AIR) continue;
      quitter.getWorld().dropItemNaturally(quitter.getBukkit().getLocation(), item);
    }

    for (ItemStack armor : quitter.getInventory().getArmorContents()) {
      if (armor == null || armor.getType() == Material.AIR) continue;
      quitter.getWorld().dropItemNaturally(quitter.getBukkit().getLocation(), armor);
    }
  }

  @EventHandler
  public void announceDynamicMapPoolChange(MapPoolAdjustEvent event) {
    // Send feedback to staff, alerting them that the map pool has changed by force
    if (event.isForced()) {
      Component poolName = TextComponent.of(event.getNewPool().getName(), TextColor.LIGHT_PURPLE);
      Component staffName =
          UsernameFormatUtils.formatStaffName(event.getSender(), event.getMatch());
      Component matchLimit =
          TextComponent.builder()
              .append(Integer.toString(event.getMatchLimit()), TextColor.GREEN)
              .append(" ")
              .append(
                  TranslatableComponent.of(
                      "match.name" + (event.getMatchLimit() != 1 ? ".plural" : ""), TextColor.GRAY))
              .build();

      // No limit
      Component forced = TranslatableComponent.of("pool.change.force", poolName, staffName);
      if (event.getTimeLimit() != null) {
        Component time =
            PeriodFormats.briefNaturalApproximate(event.getTimeLimit()).color(TextColor.GREEN);

        // If time & match limit are present, display both
        if (event.getMatchLimit() != 0) {
          Component timeAndLimit =
              TranslatableComponent.of("misc.or", TextColor.GRAY, time, matchLimit);
          forced =
              TranslatableComponent.of("pool.change.forceTimed", poolName, timeAndLimit, staffName);
        } else {
          // Just time limit
          forced = TranslatableComponent.of("pool.change.forceTimed", poolName, time, staffName);
        }
      } else if (event.getMatchLimit() != 0) {
        // Just match limit
        forced =
            TranslatableComponent.of("pool.change.forceTimed", poolName, matchLimit, staffName);
      }

      ChatDispatcher.broadcastAdminChatMessage(forced.color(TextColor.GRAY), event.getMatch());
    }

    // Broadcast map pool changes due to size
    if (event.getNewPool().isDynamic()) {
      Component broadcast =
          TextComponent.builder()
              .append("[", TextColor.WHITE)
              .append(TranslatableComponent.of("pool.name", TextColor.GOLD))
              .append("] ", TextColor.WHITE)
              .append(
                  TranslatableComponent.of(
                      "pool.change",
                      TextColor.GREEN,
                      TextComponent.of(event.getNewPool().getName(), TextColor.AQUA)))
              .build();

      event.getMatch().sendMessage(broadcast);
    }
  }

  private long lastStaffMessage = 0;

  @Override
  public void onPluginMessageReceived(String s, Player player, byte[] bytes) {
    DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
    try {
      String message = in.readUTF();
      if (message.trim().equalsIgnoreCase("CMIStaffSubChannel")) {
        short sh = in.readShort();
        byte[] morebytes = new byte[sh];
        in.readFully(morebytes);
        DataInputStream in2 = new DataInputStream(new ByteArrayInputStream(morebytes));
        String name = in2.readUTF();
        String text = in2.readUTF();
        long time = in2.readLong();
        if (time == lastStaffMessage) return;
        if (time == ChatDispatcher.get().lastStaffMessage
            || (text.equals(ChatDispatcher.get().lastMessage)
                && name.equals(ChatDispatcher.get().lastMessageName))) return;
        lastStaffMessage = time;

        Match match = this.mm.getMatches().next();
        ChatDispatcher.broadcastAdminChatMessage(
            TextComponent.of("[BUNGEE] ", TextColor.AQUA)
                .append(TextComponent.of(name, TextColor.GREEN))
                .append(TextComponent.of(": " + text, TextColor.YELLOW)),
            match);
      } else if (message.trim().equalsIgnoreCase("GetServers")) {
        String[] list = in.readUTF().split(", ");
        ChatDispatcher.get().getServers().clear();
        ChatDispatcher.get().getServers().addAll(Lists.newArrayList(list));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
