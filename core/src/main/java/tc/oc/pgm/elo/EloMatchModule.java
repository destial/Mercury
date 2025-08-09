package tc.oc.pgm.elo;

import java.util.concurrent.TimeUnit;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.event.KillstreakEvent;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchModule;
import tc.oc.pgm.api.match.MatchScope;
import tc.oc.pgm.api.match.event.MatchFinishEvent;
import tc.oc.pgm.api.party.Competitor;
import tc.oc.pgm.api.party.event.CompetitorScoreChangeEvent;
import tc.oc.pgm.api.player.GlobalStats;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.MatchPlayerState;
import tc.oc.pgm.api.player.ParticipantState;
import tc.oc.pgm.api.player.event.MatchPlayerDeathEvent;
import tc.oc.pgm.api.setting.SettingKey;
import tc.oc.pgm.api.setting.SettingValue;
import tc.oc.pgm.core.CoreLeakEvent;
import tc.oc.pgm.destroyable.DestroyableDestroyedEvent;
import tc.oc.pgm.events.ListenerScope;
import tc.oc.pgm.events.PlayerJoinPartyEvent;
import tc.oc.pgm.flag.event.FlagCaptureEvent;
import tc.oc.pgm.goals.Contribution;
import tc.oc.pgm.tablist.MatchTabInvalidateEvent;
import tc.oc.pgm.util.chat.Sound;
import tc.oc.pgm.wool.PlayerWoolPlaceEvent;

@ListenerScope(MatchScope.LOADED)
public class EloMatchModule implements MatchModule, Listener {
  private final Match match;
  private boolean isElo;

  private static final Sound SOUND = new Sound("random.click", 1, 2);

  public EloMatchModule(Match match) {
    this.match = match;
    isElo = false;
  }

  private void updateEloStatus() {
    int eloPlayers = match.getParticipants().size();
    boolean update = false;
    if (eloPlayers < PGM.get().getConfiguration().getEloConfig().getMaxPlayers() && isElo) {
      isElo = false;
      match.sendMessage(
          TextComponent.of("The match will not be recording elo points!", TextColor.RED));
      update = true;
    } else if (eloPlayers >= PGM.get().getConfiguration().getEloConfig().getMaxPlayers()
        && !isElo) {
      isElo = true;
      match.sendMessage(
          TextComponent.of("The match will be recording elo points!", TextColor.GREEN));
      match.playSound(SOUND);
      update = true;
    }

    if (update) {
      match.callEvent(new MatchTabInvalidateEvent(match));
    }
  }

  private void sendEloStatus(MatchPlayer player, int points) {
    if (player.getSettings().getValue(SettingKey.ELO) == SettingValue.ELO_ON) {
      if (player.getBukkit().isOnline()) {
        player.sendMessage(
            TextComponent.of("Elo (" + player.getStats().getElo() + ") ", TextColor.BLUE)
                .append(
                    TextComponent.of(
                        (points >= 0 ? "+" : "") + points,
                        points <= 0 ? TextColor.RED : TextColor.GREEN)));
      }
    }
  }

  @EventHandler
  public void onJoin(PlayerJoinPartyEvent e) {
    match.getExecutor(MatchScope.LOADED).schedule(this::updateEloStatus, 1L, TimeUnit.SECONDS);
  }

  @EventHandler
  public void onLeave(PlayerQuitEvent e) {
    MatchPlayer player = match.getPlayer(e.getPlayer());
    if (player == null) return;
    if (player.getParty() instanceof Competitor && match.isRunning()) {
      if (isElo) {
        EloConfig.EloOptions leaveElo =
            PGM.get().getConfiguration().getEloConfig().getSettings().get("leaving");
        if (leaveElo != null && leaveElo.enabled) {
          GlobalStats stats = player.getStats();
          int points =
              leaveElo.useScale ? (int) (stats.getElo() * leaveElo.scale) : leaveElo.points;
          stats.addElo(points);
          sendEloStatus(player, points);
        }
      }
    }
    match.getExecutor(MatchScope.LOADED).schedule(this::updateEloStatus, 1L, TimeUnit.SECONDS);
  }

  public boolean hasElo(MatchPlayer player) {
    return player.getSettings().getValue(SettingKey.ELO) == SettingValue.ELO_ON;
  }

  @EventHandler
  public void onKill(MatchPlayerDeathEvent e) {
    if (!isElo && !hasElo(e.getVictim())) return;

    EloConfig.EloOptions killElo =
        PGM.get().getConfiguration().getEloConfig().getSettings().get("kill");
    EloConfig.EloOptions suicide =
        PGM.get().getConfiguration().getEloConfig().getSettings().get("suicide");
    EloConfig.EloOptions predicted =
        PGM.get().getConfiguration().getEloConfig().getSettings().get("predicted");
    MatchPlayer victim = e.getVictim();
    ParticipantState killer = e.getKiller();

    if ((e.isSelfKill() || e.isSuicide() || killer == null) && suicide != null && suicide.enabled) {
      int points =
          suicide.useScale ? (int) (victim.getStats().getElo() * suicide.scale) : suicide.points;
      victim.getStats().addElo(points);
      sendEloStatus(victim, points);
      if (e.isPredicted() && predicted != null && predicted.enabled) {
        int predictedPoints =
            predicted.useScale
                ? (int) (victim.getStats().getElo() * predicted.scale)
                : predicted.points;
        victim.getStats().addElo(predictedPoints);
        sendEloStatus(victim, predictedPoints);
      }
      return;
    }
    if (killer != null && killElo != null && killElo.enabled) {
      GlobalStats stats = PGM.get().getDatastore().getStats(killer.getId());
      int points =
          killElo.useScale ? (int) (victim.getStats().getElo() * killElo.scale) : killElo.points;
      stats.addElo(e.isTeamKill() ? -points : points);
      victim.getStats().addElo(-points);
      sendEloStatus(victim, -points);
      if (e.isPredicted() && predicted != null && predicted.enabled) {
        int predictedPoints =
            predicted.useScale
                ? (int) (victim.getStats().getElo() * predicted.scale)
                : predicted.points;
        victim.getStats().addElo(predictedPoints);
        sendEloStatus(victim, predictedPoints);
      }
      if (killer.getPlayer().isPresent()) {
        sendEloStatus(killer.getPlayer().get(), e.isTeamKill() ? -points : points);
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onMatchEnd(MatchFinishEvent e) {
    if (!isElo) return;

    EloConfig.EloOptions mvpElo =
        PGM.get().getConfiguration().getEloConfig().getSettings().get("mvp");
    if (mvpElo != null && mvpElo.enabled) {
      ParticipantState mvp = e.mostKill;
      if (mvp != null) {
        GlobalStats stats = PGM.get().getDatastore().getStats(mvp.getId());
        int points = mvpElo.useScale ? (int) (stats.getElo() * mvpElo.scale) : mvpElo.points;
        stats.addElo(points);
        if (mvp.getPlayer().isPresent()) {
          sendEloStatus(mvp.getPlayer().get(), points);
        }
      }
    }

    EloConfig.EloOptions winElo =
        PGM.get().getConfiguration().getEloConfig().getSettings().get("win");
    EloConfig.EloOptions loseElo =
        PGM.get().getConfiguration().getEloConfig().getSettings().get("lose");

    if (e.getWinner() == null) return;

    for (MatchPlayer player : e.getMatch().getPlayers()) {
      GlobalStats stats = player.getStats();

      if (e.getWinner() == player.getParty() && winElo != null && winElo.enabled) {
        int points = winElo.useScale ? (int) (stats.getElo() * winElo.scale) : winElo.points;
        stats.addElo(points);
        sendEloStatus(player, points);
      } else if (player.getParty() instanceof Competitor && loseElo != null && loseElo.enabled) {
        int points = loseElo.useScale ? (int) (stats.getElo() * loseElo.scale) : loseElo.points;
        stats.addElo(points);
        sendEloStatus(player, points);
      }
    }
  }

  @EventHandler
  public void onKillstreak(KillstreakEvent e) {
    if (!isElo && !hasElo(e.getPlayer())) return;

    EloConfig.EloOptions streakElo =
        PGM.get()
            .getConfiguration()
            .getEloConfig()
            .getSettings()
            .get(e.getAmount() + "-killstreak");

    if (streakElo == null || !streakElo.enabled) return;
    GlobalStats stats = e.getPlayer().getStats();
    int points = streakElo.useScale ? (int) (stats.getElo() * streakElo.scale) : streakElo.points;
    stats.addElo(points);
    sendEloStatus(e.getPlayer(), points);
  }

  @EventHandler
  public void onWool(PlayerWoolPlaceEvent e) {
    MatchPlayer player = e.getPlayer().getPlayer().orElse(null);
    if (!isElo && (player != null && !hasElo(player))) return;

    EloConfig.EloOptions woolElo =
        PGM.get().getConfiguration().getEloConfig().getSettings().get("objective");
    if (woolElo == null || !woolElo.enabled) return;

    ParticipantState placer = e.getPlayer();
    if (placer == null) return;

    GlobalStats stats = PGM.get().getDatastore().getStats(placer.getId());
    int points = woolElo.useScale ? (int) (stats.getElo() * woolElo.scale) : woolElo.points;
    stats.addElo(points);
    if (placer.getPlayer().isPresent()) {
      sendEloStatus(placer.getPlayer().get(), points);
    }
  }

  @EventHandler
  public void onMonument(DestroyableDestroyedEvent e) {
    if (!isElo) return;

    EloConfig.EloOptions monElo =
        PGM.get().getConfiguration().getEloConfig().getSettings().get("objective");
    if (monElo == null || !monElo.enabled) return;

    for (Contribution contribution : e.getDestroyable().getContributions()) {
      MatchPlayerState destroyer = contribution.getPlayerState();
      if (destroyer == null) continue;

      GlobalStats stats = PGM.get().getDatastore().getStats(destroyer.getId());
      int points = monElo.useScale ? (int) (stats.getElo() * monElo.scale) : monElo.points;
      stats.addElo(points);
      if (destroyer.getPlayer().isPresent()) {
        sendEloStatus(destroyer.getPlayer().get(), points);
      }
    }
  }

  @EventHandler
  public void onCore(CoreLeakEvent e) {
    if (!isElo) return;

    EloConfig.EloOptions coreElo =
        PGM.get().getConfiguration().getEloConfig().getSettings().get("objective");
    if (coreElo == null || !coreElo.enabled) return;

    for (Contribution contribution : e.getCore().getContributions()) {
      MatchPlayerState leaker = contribution.getPlayerState();
      if (leaker == null) continue;

      GlobalStats stats = PGM.get().getDatastore().getStats(leaker.getId());
      int points = coreElo.useScale ? (int) (stats.getElo() * coreElo.scale) : coreElo.points;
      stats.addElo(points);
      if (leaker.getPlayer().isPresent()) {
        sendEloStatus(leaker.getPlayer().get(), points);
      }
    }
  }

  @EventHandler
  public void onFlag(FlagCaptureEvent e) {
    if (!isElo && !hasElo(e.getCarrier())) return;

    EloConfig.EloOptions flagElo =
        PGM.get().getConfiguration().getEloConfig().getSettings().get("objective");
    if (flagElo == null || !flagElo.enabled) return;

    MatchPlayer placer = e.getCarrier();
    if (placer == null) return;

    GlobalStats stats = placer.getStats();
    int points = flagElo.useScale ? (int) (stats.getElo() * flagElo.scale) : flagElo.points;
    stats.addElo(points);
    sendEloStatus(placer, points);
  }

  @EventHandler
  public void onScore(CompetitorScoreChangeEvent e) {}
}
