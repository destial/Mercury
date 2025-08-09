package tc.oc.pgm.modules;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.text.TextComponent;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchModule;
import tc.oc.pgm.api.match.MatchScope;
import tc.oc.pgm.api.match.event.MatchFinishEvent;
import tc.oc.pgm.api.party.Competitor;
import tc.oc.pgm.api.player.GlobalStats;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.PlayerRelation;
import tc.oc.pgm.api.player.Username;
import tc.oc.pgm.api.player.event.MatchPlayerDeathEvent;
import tc.oc.pgm.api.tracker.info.CauseInfo;
import tc.oc.pgm.core.CoreLeakEvent;
import tc.oc.pgm.destroyable.DestroyableDestroyedEvent;
import tc.oc.pgm.events.ListenerScope;
import tc.oc.pgm.flag.event.FlagCaptureEvent;
import tc.oc.pgm.goals.Contribution;
import tc.oc.pgm.teams.Team;
import tc.oc.pgm.teams.TeamMatchModule;
import tc.oc.pgm.tracker.Trackers;
import tc.oc.pgm.tracker.info.ProjectileInfo;
import tc.oc.pgm.util.chat.Audience;
import tc.oc.pgm.wool.PlayerWoolPlaceEvent;

@ListenerScope(MatchScope.LOADED)
public class GlobalStatsMatchModule implements MatchModule, Listener {
  private final Match match;

  public GlobalStatsMatchModule(Match match) {
    this.match = match;
  }

  private String getName(OfflinePlayer player) {
    if (player != null && player.getName() != null) return player.getName();
    if (player != null) {
      Username username = PGM.get().getDatastore().getUsername(player.getUniqueId());
      return username.getNameLegacy() != null ? username.getNameLegacy() : "Unknown";
    }
    return "Unknown";
  }

  private Map.Entry<UUID, Integer> sortStats(Map<UUID, Integer> map) {
    return map.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).orElse(null);
  }

  private Map.Entry<UUID, Double> sortStatsDouble(Map<UUID, Double> map) {
    return map.entrySet().stream()
        .max(Comparator.comparingDouble(Map.Entry::getValue))
        .orElse(null);
  }

  private Map.Entry<UUID, Float> sortStatsFloat(Map<UUID, Float> map) {
    return map.entrySet().stream()
        .max(Comparator.comparingDouble(Map.Entry::getValue))
        .orElse(null);
  }

  public void sendTopGlobalStatsMessage(Audience audience, GlobalStats.Leaderboard leaderboard) {
    Map<UUID, GlobalStats> all = GlobalStats.allStats();
    if (all.isEmpty()) {
      audience.sendWarning(
          TextComponent.of("Unable to show! Still loading stats of all players..."));
      return;
    }
    Map<UUID, Double> top = new HashMap<>();
    for (Map.Entry<UUID, GlobalStats> entry : all.entrySet()) {
      GlobalStats stats = entry.getValue();
      UUID uuid = entry.getKey();
      switch (leaderboard) {
        case WINS:
          top.put(uuid, (double) stats.getWins());
          break;
        case BOW:
          top.put(uuid, (double) stats.getLongestShot());
          break;
        case KDR:
          if (stats.getDeaths() >= 100) top.put(uuid, (double) stats.getKDR());
          break;
        case MVP:
          top.put(uuid, (double) stats.getMVP());
          break;
        case CORES:
          top.put(uuid, (double) stats.getCoresLeaked());
          break;
        case FLAGS:
          top.put(uuid, (double) stats.getFlagsCaptured());
          break;
        case KILLS:
          top.put(uuid, (double) stats.getKills());
          break;
        case WOOLS:
          top.put(uuid, (double) stats.getWoolsCaptured());
          break;
        case DEATHS:
          top.put(uuid, (double) stats.getDeaths());
          break;
        case LOSSES:
          top.put(uuid, (double) stats.getLosses());
          break;
        case MONUMENTS:
          top.put(uuid, (double) stats.getMonumentsBroken());
          break;
        case KILLSTREAK:
          top.put(uuid, (double) stats.getHighestKS());
          break;
        case WLR:
          if (stats.getLosses() >= 10) top.put(uuid, stats.getWins() / (double) stats.getLosses());
          break;
        case ELO:
          top.put(uuid, (double) stats.getElo());
          break;
        case ARROWS:
          top.put(uuid, (double) stats.getArrowsHit());
          break;
        case SCORE:
          top.put(uuid, stats.getCombatScore());
          break;
        default:
          break;
      }
    }
    List<Map.Entry<UUID, Double>> list =
        top.entrySet().stream()
            .sorted(Comparator.comparingDouble(Map.Entry::getValue))
            .collect(Collectors.toList());
    Collections.reverse(list);
    int i = 0;
    DecimalFormat df = new DecimalFormat("#.##");
    for (Map.Entry<UUID, Double> entry : list) {
      if (++i > 10) break;
      double value = entry.getValue();
      OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
      audience.sendMessage(
          TranslatableComponent.of(
              "match.stats.top",
              TextColor.GRAY,
              TextComponent.of(i, TextColor.YELLOW),
              TextComponent.of(
                  value == (int) value ? "" + (int) value : df.format(value),
                  (leaderboard == GlobalStats.Leaderboard.DEATHS
                          || leaderboard == GlobalStats.Leaderboard.LOSSES
                      ? TextColor.RED
                      : TextColor.GREEN)),
              TextComponent.of(getName(player), TextColor.YELLOW)));
    }
    if (audience instanceof MatchPlayer) {
      MatchPlayer player = (MatchPlayer) audience;
      int position = list.size() + 1;
      for (int j = 0; j < list.size(); j++) {
        Map.Entry<UUID, Double> entry = list.get(j);
        if (entry.getKey().equals(player.getId())) {
          position = j + 1;
          break;
        }
      }
      player.sendMessage(
          TextComponent.of("Your position: " + position + posTitle(position), TextColor.GREEN));
    }
  }

  private String posTitle(int pos) {
    String s = "" + pos;
    if (s.endsWith("1") && pos != 11) {
      return "st";
    } else if (s.endsWith("2") && pos != 12) {
      return "nd";
    } else if (s.endsWith("3") && pos != 13) {
      return "rd";
    }
    return "th";
  }

  public void sendTopGlobalStatsMessage(Audience audience) {
    Map<UUID, GlobalStats> all = GlobalStats.allStats();
    if (all.isEmpty()) {
      audience.sendWarning(
          TextComponent.of("Unable to show! Still loading stats of all players..."));
      return;
    }
    Map<UUID, Integer> kills = new HashMap<>();
    Map<UUID, Integer> deaths = new HashMap<>();
    Map<UUID, Integer> wins = new HashMap<>();
    Map<UUID, Integer> losses = new HashMap<>();
    Map<UUID, Integer> monuments = new HashMap<>();
    Map<UUID, Integer> flags = new HashMap<>();
    Map<UUID, Integer> wools = new HashMap<>();
    Map<UUID, Integer> cores = new HashMap<>();
    Map<UUID, Integer> longestShot = new HashMap<>();
    Map<UUID, Integer> highestKs = new HashMap<>();
    Map<UUID, Float> kdr = new HashMap<>();
    Map<UUID, Float> wlr = new HashMap<>();
    Map<UUID, Integer> mvps = new HashMap<>();
    Map<UUID, Integer> elos = new HashMap<>();
    Map<UUID, Integer> arrows = new HashMap<>();
    Map<UUID, Double> score = new HashMap<>();
    for (Map.Entry<UUID, GlobalStats> entry : all.entrySet()) {
      GlobalStats stats = entry.getValue();
      UUID uuid = entry.getKey();
      kills.put(uuid, stats.getKills());
      deaths.put(uuid, stats.getDeaths());
      wins.put(uuid, stats.getWins());
      losses.put(uuid, stats.getLosses());
      monuments.put(uuid, stats.getMonumentsBroken());
      flags.put(uuid, stats.getFlagsCaptured());
      wools.put(uuid, stats.getWoolsCaptured());
      cores.put(uuid, stats.getCoresLeaked());
      longestShot.put(uuid, stats.getLongestShot());
      highestKs.put(uuid, stats.getHighestKS());
      if (stats.getDeaths() >= 100) kdr.put(uuid, stats.getKDR());
      mvps.put(uuid, stats.getMVP());
      if (stats.getLosses() >= 10) wlr.put(uuid, stats.getWins() / (float) (stats.getLosses()));
      elos.put(uuid, stats.getElo());
      arrows.put(uuid, stats.getArrowsHit());
      score.put(uuid, stats.getCombatScore());
    }
    Map.Entry<UUID, Integer> mostKills = sortStats(kills);
    Map.Entry<UUID, Integer> mostDeaths = sortStats(deaths);
    Map.Entry<UUID, Integer> mostWins = sortStats(wins);
    Map.Entry<UUID, Integer> mostLosses = sortStats(losses);
    Map.Entry<UUID, Integer> mostMonuments = sortStats(monuments);
    Map.Entry<UUID, Integer> mostCores = sortStats(cores);
    Map.Entry<UUID, Integer> mostFlags = sortStats(flags);
    Map.Entry<UUID, Integer> mostWools = sortStats(wools);
    Map.Entry<UUID, Integer> mostKs = sortStats(highestKs);
    Map.Entry<UUID, Integer> shot = sortStats(longestShot);
    Map.Entry<UUID, Integer> mostMvps = sortStats(mvps);
    Map.Entry<UUID, Float> highestKdr = sortStatsFloat(kdr);
    Map.Entry<UUID, Float> highestWlr = sortStatsFloat(wlr);
    Map.Entry<UUID, Integer> highestElo = sortStats(elos);
    Map.Entry<UUID, Integer> highestArrows = sortStats(arrows);
    Map.Entry<UUID, Double> highestCombatScore = sortStatsDouble(score);
    UUID random = UUID.randomUUID();

    OfflinePlayer kp = Bukkit.getOfflinePlayer(mostKills != null ? mostKills.getKey() : random);
    OfflinePlayer dp = Bukkit.getOfflinePlayer(mostDeaths != null ? mostDeaths.getKey() : random);
    OfflinePlayer wp = Bukkit.getOfflinePlayer(mostWins != null ? mostWins.getKey() : random);
    OfflinePlayer lp = Bukkit.getOfflinePlayer(mostLosses != null ? mostLosses.getKey() : random);
    OfflinePlayer mp = Bukkit.getOfflinePlayer(mostMonuments.getKey());
    OfflinePlayer cp = Bukkit.getOfflinePlayer(mostCores.getKey());
    OfflinePlayer fp = Bukkit.getOfflinePlayer(mostFlags.getKey());
    OfflinePlayer wop = Bukkit.getOfflinePlayer(mostWools.getKey());
    OfflinePlayer ksp = Bukkit.getOfflinePlayer(mostKs.getKey());
    OfflinePlayer lsp = Bukkit.getOfflinePlayer(shot.getKey());
    OfflinePlayer kdp = Bukkit.getOfflinePlayer(highestKdr.getKey());
    OfflinePlayer mvvp = Bukkit.getOfflinePlayer(mostMvps.getKey());
    OfflinePlayer wlp = Bukkit.getOfflinePlayer(highestWlr.getKey());
    OfflinePlayer elp = Bukkit.getOfflinePlayer(highestElo.getKey());
    OfflinePlayer alp = Bukkit.getOfflinePlayer(highestArrows.getKey());
    OfflinePlayer slp = Bukkit.getOfflinePlayer(highestCombatScore.getKey());
    DecimalFormat df = new DecimalFormat("#.##");
    audience.sendMessage(
        TranslatableComponent.of(
            "match.stats.combatScore.by",
            TextColor.GRAY,
            TextComponent.of(getName(slp), TextColor.YELLOW),
            TextComponent.of(df.format(highestCombatScore.getValue()), TextColor.GREEN)));
    audience.sendMessage(
        TranslatableComponent.of(
            "match.stats.killsDeaths",
            TextColor.GRAY,
            TextComponent.of(getName(kp), TextColor.YELLOW),
            TextComponent.of(mostKills.getValue(), TextColor.GREEN),
            TextComponent.of(getName(dp), TextColor.YELLOW),
            TextComponent.of(mostDeaths.getValue(), TextColor.RED),
            TextComponent.of(getName(kdp), TextColor.YELLOW),
            TextComponent.of(df.format(highestKdr.getValue()), TextColor.GREEN)));
    audience.sendMessage(
        TranslatableComponent.of(
            "match.stats.winnings.by",
            TextColor.GRAY,
            TextComponent.of(getName(wp), TextColor.YELLOW),
            TextComponent.of(mostWins.getValue(), TextColor.GREEN),
            TextComponent.of(getName(lp), TextColor.YELLOW),
            TextComponent.of(mostLosses.getValue(), TextColor.RED),
            TextComponent.of(getName(wlp), TextColor.YELLOW),
            TextComponent.of(df.format(highestWlr.getValue()), TextColor.GREEN)));
    audience.sendMessage(
        TranslatableComponent.of(
            "match.stats.elo.by",
            TextColor.GRAY,
            TextComponent.of(getName(elp), TextColor.YELLOW),
            TextComponent.of(highestElo.getValue(), TextColor.GREEN)));
    audience.sendMessage(
        TranslatableComponent.of(
            "match.stats.mvp.by",
            TextColor.GRAY,
            TextComponent.of(getName(mvvp), TextColor.YELLOW),
            TextComponent.of(mostMvps.getValue(), TextColor.GREEN)));
    audience.sendMessage(
        TranslatableComponent.of(
            "match.stats.killstreak",
            TextColor.GRAY,
            TextComponent.of(getName(ksp), TextColor.YELLOW),
            TextComponent.of(mostKs.getValue(), TextColor.GREEN)));
    audience.sendMessage(
        TranslatableComponent.of(
            "match.stats.bowshot",
            TextColor.GRAY,
            TextComponent.of(getName(lsp), TextColor.YELLOW),
            TextComponent.of(shot.getValue(), TextColor.GREEN)));
    audience.sendMessage(
        TranslatableComponent.of(
            "match.stats.mostarrows",
            TextColor.GRAY,
            TextComponent.of(getName(alp), TextColor.YELLOW),
            TextComponent.of(highestArrows.getValue(), TextColor.GREEN)));
    audience.sendMessage(
        TranslatableComponent.of(
            "match.stats.monumentsBroken.by",
            TextColor.GRAY,
            TextComponent.of(getName(mp), TextColor.YELLOW),
            TextComponent.of(mostMonuments.getValue(), TextColor.GREEN)));
    audience.sendMessage(
        TranslatableComponent.of(
            "match.stats.coresLeaked.by",
            TextColor.GRAY,
            TextComponent.of(getName(cp), TextColor.YELLOW),
            TextComponent.of(mostCores.getValue(), TextColor.GREEN)));
    audience.sendMessage(
        TranslatableComponent.of(
            "match.stats.flagsCaptured.by",
            TextColor.GRAY,
            TextComponent.of(getName(fp), TextColor.YELLOW),
            TextComponent.of(mostFlags.getValue(), TextColor.GREEN)));
    audience.sendMessage(
        TranslatableComponent.of(
            "match.stats.woolsCaptured.by",
            TextColor.GRAY,
            TextComponent.of(getName(wop), TextColor.YELLOW),
            TextComponent.of(mostWools.getValue(), TextColor.GREEN)));
  }

  public void sendGlobalStatsMessage(Audience audience, GlobalStats stats) {
    DecimalFormat df = new DecimalFormat("#.##");
    audience.sendMessage(
        TranslatableComponent.of(
            "match.stats",
            TextColor.GRAY,
            TextComponent.of(Integer.toString(stats.getKills()), TextColor.GREEN),
            TextComponent.of(Integer.toString(stats.getHighestKS()), TextColor.GREEN),
            TextComponent.of(Integer.toString(stats.getDeaths()), TextColor.RED),
            TextComponent.of(df.format(stats.getKDR()), TextColor.GREEN),
            TextComponent.of(Integer.toString(stats.getLongestShot()), TextColor.YELLOW)));
    audience.sendMessage(
        TranslatableComponent.of(
            "match.stats.combatScore",
            TextColor.GRAY,
            TextComponent.of(df.format(stats.getCombatScore()), TextColor.GREEN)));
    audience.sendMessage(
        TranslatableComponent.of(
            "match.stats.winnings",
            TextColor.GRAY,
            TextComponent.of(stats.getWins(), TextColor.GREEN),
            TextComponent.of(stats.getLosses(), TextColor.RED),
            TextComponent.of(
                df.format(
                    stats.getWins() / (double) (stats.getLosses() == 0 ? 1 : stats.getLosses())),
                TextColor.GREEN)));
    audience.sendMessage(
        TranslatableComponent.of(
            "match.stats.elo", TextColor.GRAY, TextComponent.of(stats.getElo(), TextColor.GREEN)));
    audience.sendMessage(
        TranslatableComponent.of(
            "match.stats.mvp", TextColor.GRAY, TextComponent.of(stats.getMVP(), TextColor.GREEN)));
    audience.sendMessage(
        TranslatableComponent.of(
            "match.stats.bowshot.by",
            TextColor.GRAY,
            TextComponent.of(stats.getLongestShot(), TextColor.GREEN)));
    audience.sendMessage(
        TranslatableComponent.of(
            "match.stats.mostarrows.by",
            TextColor.GRAY,
            TextComponent.of(stats.getArrowsHit(), TextColor.GREEN)));
    audience.sendMessage(
        TranslatableComponent.of(
            "match.stats.monumentsBroken",
            TextColor.GRAY,
            TextComponent.of(stats.getMonumentsBroken(), TextColor.GREEN)));
    audience.sendMessage(
        TranslatableComponent.of(
            "match.stats.coresLeaked",
            TextColor.GRAY,
            TextComponent.of(stats.getCoresLeaked(), TextColor.GREEN)));
    audience.sendMessage(
        TranslatableComponent.of(
            "match.stats.flagsCaptured",
            TextColor.GRAY,
            TextComponent.of(stats.getFlagsCaptured(), TextColor.GREEN)));
    audience.sendMessage(
        TranslatableComponent.of(
            "match.stats.woolsCaptured",
            TextColor.GRAY,
            TextComponent.of(stats.getWoolsCaptured(), TextColor.GREEN)));
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent e) {
    PGM.get().getDatastore().getStats(e.getPlayer().getUniqueId());
    PGM.get().getDatastore().getCoins(e.getPlayer().getUniqueId());
    PGM.get().getDatastore().getPlayerData(e.getPlayer().getUniqueId());
    PGM.get()
        .getDatastore()
        .getUsername(e.getPlayer().getUniqueId())
        .setName(e.getPlayer().getName());
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onKill(MatchPlayerDeathEvent event) {
    MatchPlayer victim = event.getVictim();
    MatchPlayer murderer = null;
    if (event.getKiller() != null)
      murderer = event.getKiller().getParty().getPlayer(event.getKiller().getId());
    victim.getStats().addDeath();
    if (murderer != null
        && PlayerRelation.get(victim.getParticipantState(), murderer) != PlayerRelation.ALLY
        && PlayerRelation.get(victim.getParticipantState(), murderer) != PlayerRelation.SELF) {
      if (event.getDamageInfo() instanceof ProjectileInfo) {
        double distance =
            Trackers.distanceFromRanged(
                (ProjectileInfo) event.getDamageInfo(),
                (victim.getBukkit() != null
                    ? victim.getBukkit().getLocation()
                    : victim.getState().getLocation()));
        if (!Double.isNaN(distance)) {
          murderer.getStats().setLongestShot((int) distance);
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
            murderer.getStats().setLongestShot((int) distance);
          }
        }
      }
      murderer.getStats().addKill();
      StatsMatchModule smm = match.needModule(StatsMatchModule.class);
      if (!smm.allPlayerStats.containsKey(murderer.getId())) return;
      murderer.getStats().setHighestKS(smm.allPlayerStats.get(murderer.getId()).getKillstreakMax());
    }
  }

  @EventHandler
  public void onMonument(DestroyableDestroyedEvent e) {
    for (Contribution contribution : e.getDestroyable().getContributions()) {
      if (contribution.getPlayerState().getPlayer().isPresent()) {
        MatchPlayer player = contribution.getPlayerState().getPlayer().get();
        player.getStats().breakMonument();
      }
    }
  }

  @EventHandler
  public void onCore(CoreLeakEvent e) {
    for (Contribution contribution : e.getCore().getContributions()) {
      if (contribution.getPlayerState().getPlayer().isPresent()) {
        MatchPlayer player = contribution.getPlayerState().getPlayer().get();
        player.getStats().leakCore();
      }
    }
  }

  @EventHandler
  public void oFlag(FlagCaptureEvent e) {
    e.getCarrier().getStats().captureFlag();
  }

  @EventHandler
  public void onWool(PlayerWoolPlaceEvent e) {
    if (e.getPlayer().getPlayer().isPresent()) {
      MatchPlayer player = e.getPlayer().getPlayer().get();
      player.getStats().captureWool();
    }
  }

  @EventHandler
  public void onEnd(MatchFinishEvent event) {
    Match match = event.getMatch();
    if (event.getWinners() == null) return;
    for (MatchPlayer viewer : match.getPlayers()) {
      for (Competitor winner : event.getWinners()) {
        if (winner == viewer.getParty()) {
          viewer.getStats().win();
        } else if (viewer.getParty() instanceof Competitor) {
          viewer.getStats().lose();
        }
      }
    }
    if (match.hasModule(TeamMatchModule.class)) {
      TeamMatchModule tmm = match.getModule(TeamMatchModule.class);
      for (Map.Entry<UUID, Team> lastLeft : tmm.lastLeft.asMap().entrySet()) {
        UUID uuid = lastLeft.getKey();
        Team team = lastLeft.getValue();
        for (Competitor winner : event.getWinners()) {
          if (winner != team) {
            GlobalStats stats = PGM.get().getDatastore().getStats(uuid);
            stats.lose();
            stats.lose();
          }
        }
      }
    }
  }
}
