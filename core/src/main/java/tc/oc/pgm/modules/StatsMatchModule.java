package tc.oc.pgm.modules;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.format.TextColor;
import net.kyori.text.format.TextDecoration;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import tc.oc.pgm.api.event.KillstreakEvent;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchModule;
import tc.oc.pgm.api.match.MatchScope;
import tc.oc.pgm.api.match.event.MatchFinishEvent;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.PlayerRelation;
import tc.oc.pgm.api.player.event.MatchPlayerDeathEvent;
import tc.oc.pgm.api.setting.SettingKey;
import tc.oc.pgm.api.setting.SettingValue;
import tc.oc.pgm.api.tracker.info.CauseInfo;
import tc.oc.pgm.events.ListenerScope;
import tc.oc.pgm.tracker.Trackers;
import tc.oc.pgm.tracker.info.ProjectileInfo;
import tc.oc.pgm.util.LegacyFormatUtils;
import tc.oc.pgm.util.named.NameStyle;
import tc.oc.pgm.util.text.TextFormatter;
import tc.oc.pgm.util.text.types.PlayerComponent;

@ListenerScope(MatchScope.RUNNING)
public class StatsMatchModule implements MatchModule, Listener {

  private final Match match;
  public final Map<UUID, PlayerStats> allPlayerStats = new HashMap<>();
  // Since Bukkit#getOfflinePlayer reads the cached user files, and those files have an expire date
  // + will be wiped if X amount of players join, we need a seperate cache for players with stats
  private final Map<UUID, String> cachedUsernames = new HashMap<>();

  public StatsMatchModule(Match match) {
    this.match = match;
  }

  public static class PlayerStats {
    private int kills = 0;
    private int deaths = 0;
    private int killstreak = 0;
    private int killstreakMax = 0;
    private int longestBowKill = 0;
    private Future<?> task;

    private void onMurder() {
      kills++;
      killstreak++;
      if (killstreak > killstreakMax) killstreakMax = killstreak;
    }

    private void onDeath() {
      deaths++;
      killstreak = 0;
    }

    private void setLongestBowKill(double distance) {
      if (distance > longestBowKill) {
        longestBowKill = (int) distance;
      }
    }

    public int getKills() {
      return kills;
    }

    public int getKillstreak() {
      return killstreak;
    }

    public int getKillstreakMax() {
      return killstreakMax;
    }

    public int getDeaths() {
      return deaths;
    }

    public int getLongestBowKill() {
      return longestBowKill;
    }

    private final DecimalFormat decimalFormatKd = new DecimalFormat("#.##");

    public Component getBasicStatsMessage() {
      String kd;
      if (deaths == 0) {
        kd = Double.toString(kills);
      } else {
        kd = decimalFormatKd.format(kills / (double) deaths);
      }
      return TranslatableComponent.of(
          "match.stats",
          TextColor.GRAY,
          TextComponent.of(Integer.toString(kills), TextColor.GREEN),
          TextComponent.of(Integer.toString(killstreak), TextColor.GREEN),
          TextComponent.of(Integer.toString(deaths), TextColor.RED),
          TextComponent.of(kd, TextColor.GREEN),
          TextComponent.of(Integer.toString(longestBowKill), TextColor.YELLOW));
    }
  }

  @EventHandler
  public void onPlayerDeath(MatchPlayerDeathEvent event) {
    MatchPlayer victim = event.getVictim();
    MatchPlayer murderer = null;

    if (event.getKiller() != null)
      murderer = event.getKiller().getParty().getPlayer(event.getKiller().getId());

    UUID victimUUID = victim.getId();
    PlayerStats victimStats = allPlayerStats.get(victimUUID);
    if (hasNoStats(victimUUID)) victimStats = putNewPlayer(victimUUID);
    victimStats.onDeath();

    if (victim.getSettings().getValue(SettingKey.STATS).equals(SettingValue.STATS_ON))
      sendPlayerStats(victim, victimStats);

    if (murderer != null
        && PlayerRelation.get(victim.getParticipantState(), murderer) != PlayerRelation.ALLY
        && PlayerRelation.get(victim.getParticipantState(), murderer) != PlayerRelation.SELF) {
      UUID murdererUUID = murderer.getId();
      PlayerStats murdererStats = allPlayerStats.get(murdererUUID);

      if (hasNoStats(murdererUUID)) murdererStats = putNewPlayer(murdererUUID);

      if (event.getDamageInfo() instanceof ProjectileInfo) {
        double distance =
            Trackers.distanceFromRanged(
                (ProjectileInfo) event.getDamageInfo(),
                (victim.getBukkit() != null
                    ? victim.getBukkit().getLocation()
                    : victim.getState().getLocation()));
        if (!Double.isNaN(distance)) {
          murdererStats.setLongestBowKill(distance);
        }
      }
      if (event.getDamageInfo() instanceof CauseInfo) {
        CauseInfo fall = (CauseInfo) event.getDamageInfo();
        if (fall.getCause() instanceof ProjectileInfo) {
          double distance =
              Trackers.distanceFromRanged(
                  (ProjectileInfo) fall.getCause(),
                  (victim.getBukkit() != null
                      ? victim.getBukkit().getLocation()
                      : victim.getState().getLocation()));
          if (!Double.isNaN(distance)) {
            murdererStats.setLongestBowKill(distance);
          }
        }
      }

      murdererStats.onMurder();
      if (murdererStats.getKillstreak() == 5) {
        sendKillstreak(murderer, 5, TextColor.GREEN);
      } else if (murdererStats.getKillstreak() == 10) {
        sendKillstreak(murderer, 10, TextColor.YELLOW);
      } else if (murdererStats.getKillstreak() == 15) {
        sendKillstreak(murderer, 15, TextColor.GOLD);
      } else if (murdererStats.getKillstreak() == 20) {
        sendKillstreak(murderer, 20, TextColor.RED);
      } else if (murdererStats.getKillstreak() >= 25 && murdererStats.getKillstreak() % 5 == 0) {
        sendKillstreak(murderer, murdererStats.getKillstreak(), TextColor.DARK_RED);
      }
    }
  }

  private void sendKillstreak(MatchPlayer murderer, int amount, TextColor color) {
    murderer
        .getMatch()
        .sendMessage(
            TranslatableComponent.of(
                "broadcast.killstreak",
                TextColor.WHITE,
                murderer.getName(NameStyle.COLOR),
                TextComponent.of(amount, color)));
    Collection<MatchPlayer> players = murderer.getMatch().getPlayers();
    murderer.getMatch().callEvent(new KillstreakEvent(murderer, amount));
    for (MatchPlayer player : players) {
      player
          .getBukkit()
          .playSound(player.getBukkit().getLocation(), Sound.ENDERDRAGON_GROWL, 1f, 0.7f);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onMatchEnd(MatchFinishEvent event) {

    if (allPlayerStats.isEmpty()) return;

    Map<UUID, Integer> allKills = new HashMap<>();
    Map<UUID, Integer> allKillstreaks = new HashMap<>();
    Map<UUID, Integer> allDeaths = new HashMap<>();
    Map<UUID, Integer> allBowshots = new HashMap<>();

    for (Map.Entry<UUID, PlayerStats> mapEntry : allPlayerStats.entrySet()) {
      UUID playerUUID = mapEntry.getKey();
      PlayerStats playerStats = mapEntry.getValue();

      if (hasNoStats(playerUUID)) playerStats = putNewPlayer(playerUUID);

      allKills.put(playerUUID, playerStats.getKills());
      allKillstreaks.put(playerUUID, playerStats.getKillstreakMax());
      allDeaths.put(playerUUID, playerStats.getDeaths());
      allBowshots.put(playerUUID, playerStats.getLongestBowKill());
    }
    Map.Entry<UUID, Integer> mostKills = sortStats(allKills);
    MatchPlayer k = match.getPlayer(mostKills.getKey());
    event.mostKill = match.getParticipantState(mostKills.getKey());
    if (k != null) k.getStats().mvp();
    Component killMessage = getMessage("match.stats.kills", mostKills, TextColor.GREEN);
    Component killstreakMessage =
        getMessage("match.stats.killstreak", sortStats(allKillstreaks), TextColor.GREEN);
    Component deathMessage = getMessage("match.stats.deaths", sortStats(allDeaths), TextColor.RED);
    Map.Entry<UUID, Integer> bestBowshot = sortStats(allBowshots);
    if (bestBowshot.getValue() == 1)
      bestBowshot.setValue(2); // Avoids translating "1 block" vs "n blocks"
    Component bowshotMessage = getMessage("match.stats.bowshot", bestBowshot, TextColor.YELLOW);

    match
        .getExecutor(MatchScope.LOADED)
        .schedule(
            () -> {
              for (MatchPlayer viewer : match.getPlayers()) {
                if (viewer.getSettings().getValue(SettingKey.STATS) == SettingValue.STATS_OFF)
                  continue;

                viewer.sendMessage(
                    TextFormatter.horizontalLineHeading(
                        viewer.getBukkit(),
                        TranslatableComponent.of("match.stats.overall", TextColor.YELLOW),
                        TextColor.GRAY));
                viewer.sendMessage(killMessage);
                viewer.sendMessage(killstreakMessage);
                viewer.sendMessage(deathMessage);
                if (bestBowshot.getValue() != 0) viewer.sendMessage(bowshotMessage);
                viewer.sendMessage(LegacyFormatUtils.horizontalLine(ChatColor.GRAY, 300));
              }
            },
            5 + 1, // NOTE: This is 1 second after the votebook appears
            TimeUnit.SECONDS);
  }

  @EventHandler
  public void onPlayerLeave(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    if (allPlayerStats.containsKey(player.getUniqueId()))
      cachedUsernames.put(player.getUniqueId(), player.getName());
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    UUID playerUUID = event.getPlayer().getUniqueId();
    cachedUsernames.remove(playerUUID);
  }

  private Map.Entry<UUID, Integer> sortStats(Map<UUID, Integer> map) {
    return map.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).orElse(null);
  }

  private void sendPlayerStats(MatchPlayer player, PlayerStats stats) {
    if (stats.task != null && !stats.task.isDone()) {
      stats.task.cancel(true);
    }
    stats.task = sendLongHotbarMessage(player, stats.getBasicStatsMessage());
  }

  private Future<?> sendLongHotbarMessage(MatchPlayer player, Component message) {
    Future<?> task =
        match
            .getExecutor(MatchScope.LOADED)
            .scheduleWithFixedDelay(() -> player.showHotbar(message), 0, 1, TimeUnit.SECONDS);

    match.getExecutor(MatchScope.LOADED).schedule(() -> task.cancel(true), 4, TimeUnit.SECONDS);

    return task;
  }

  Component getMessage(String messageKey, Map.Entry<UUID, Integer> mapEntry, TextColor color) {
    return TranslatableComponent.of(
        messageKey,
        TextColor.GRAY,
        playerName(mapEntry.getKey()),
        TextComponent.of(Integer.toString(mapEntry.getValue()), color, TextDecoration.BOLD));
  }

  private Component playerName(UUID playerUUID) {
    return PlayerComponent.of(
        Bukkit.getPlayer(playerUUID),
        cachedUsernames.getOrDefault(playerUUID, "Unknown"),
        NameStyle.FANCY);
  }

  public boolean hasNoStats(UUID player) {
    return !allPlayerStats.containsKey(player);
  }

  private PlayerStats putNewPlayer(UUID player) {
    allPlayerStats.put(player, new PlayerStats());
    return allPlayerStats.get(player);
  }

  public Component getBasicStatsMessage(UUID player) {
    if (hasNoStats(player)) return putNewPlayer(player).getBasicStatsMessage();
    return allPlayerStats.get(player).getBasicStatsMessage();
  }
}
