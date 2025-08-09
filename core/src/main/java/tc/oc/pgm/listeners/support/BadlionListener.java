package tc.oc.pgm.listeners.support;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.badlion.bukkitapi.BukkitBadlionApi;
import net.badlion.bukkitapi.timers.Timer;
import net.badlion.bukkitapi.timers.TimerApi;
import net.badlion.modapicommon.utility.AbstractTeamMarkerManager;
import net.badlion.modapicommon.utility.TeamMemberLocation;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.event.MatchFinishEvent;
import tc.oc.pgm.api.match.event.MatchStartEvent;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.event.MatchPlayerAddEvent;
import tc.oc.pgm.events.PlayerJoinPartyEvent;
import tc.oc.pgm.events.PlayerLeavePartyEvent;
import tc.oc.pgm.match.ObservingParty;
import tc.oc.pgm.teams.Team;
import tc.oc.pgm.timelimit.TimeLimitMatchModule;
import tc.oc.pgm.timelimit.events.TimeLimitChangeEvent;

public class BadlionListener implements Listener {
  private final AbstractTeamMarkerManager teamModule;
  private final TimerApi timerModule;
  private Timer timer;
  private BukkitTask task;

  public BadlionListener() {
    log("Found BadlionClientModAPI! Hooking into it!");
    teamModule = BukkitBadlionApi.getInstance().getTeamMarkerManager();
    timerModule = TimerApi.getInstance();
  }

  private void task(Match match) {
    if (task != null) task.cancel();
    task =
        Bukkit.getScheduler()
            .runTaskTimer(
                PGM.get(),
                () -> {
                  for (MatchPlayer matchPlayer : match.getPlayers()) {
                    if (matchPlayer == null) continue;

                    if (matchPlayer.isObserving()) {
                      sendNoTeammates(matchPlayer);
                      continue;
                    }
                    sendTeammates(matchPlayer);
                  }
                },
                0L,
                5L);
  }

  @EventHandler
  public void onPlayerJoinParty(PlayerJoinPartyEvent e) {
    if (e.getOldParty() instanceof Team) {
      for (MatchPlayer p : e.getOldParty().getPlayers()) {
        if (p == e.getPlayer()) continue;
        sendTeammates(p);
      }
    }

    if (e.getNewParty() instanceof Team) {
      for (MatchPlayer p : e.getNewParty().getPlayers()) {
        sendTeammates(p);
      }
    }

    if (e.getNewParty() instanceof ObservingParty) {
      sendNoTeammates(e.getPlayer());
    }
  }

  @EventHandler
  public void onMatchStart(MatchStartEvent e) {
    task(e.getMatch());

    Match match = e.getMatch();
    TimeLimitMatchModule tm = match.getModule(TimeLimitMatchModule.class);
    if (tm == null) return;
    Duration remaining = tm.getFinalRemaining();
    if (remaining == null) return;
    timer =
        timerModule.createTimeTimer(
            "Time Remaining",
            new ItemStack(Material.WATCH),
            false,
            remaining.getSeconds(),
            TimeUnit.SECONDS);
    match.getPlayers().forEach(p -> timer.addReceiver(p.getBukkit()));
  }

  @EventHandler
  public void onMatchPlayerJoin(MatchPlayerAddEvent e) {
    if (timer == null) return;
    timer.addReceiver(e.getPlayer().getBukkit());
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent e) {
    if (timer == null) return;
    timer.removeReceiver(e.getPlayer());
  }

  @EventHandler
  public void onTimeChange(TimeLimitChangeEvent e) {
    Match match = e.getMatch();
    Duration remaining = match.needModule(TimeLimitMatchModule.class).getFinalRemaining();
    if (remaining == null) return;
    if (timer != null) {
      for (MatchPlayer player : e.getMatch().getPlayers()) {
        timer.removeReceiver(player.getBukkit());
      }
      timerModule.removeTimer(timer);
    }
    timer =
        timerModule.createTimeTimer(
            "Time Remaining",
            new ItemStack(Material.WATCH),
            false,
            remaining.getSeconds(),
            TimeUnit.SECONDS);
    match.getPlayers().forEach(p -> timer.addReceiver(p.getBukkit()));
  }

  @EventHandler
  public void onMatchEnd(MatchFinishEvent e) {
    task.cancel();
    task = null;
    for (MatchPlayer player : e.getMatch().getPlayers()) {
      if (player == null) continue;
      sendNoTeammates(player);
    }
    if (timer != null) {
      timerModule.removeTimer(timer);
      timer = null;
    }
  }

  public void sendTeammates(MatchPlayer p) {
    if (p == null || p.getBukkit() == null || !p.getBukkit().isOnline()) return;
    List<TeamMemberLocation> map = new ArrayList<>();
    Color partyColor = p.getParty().getFullColor();
    java.awt.Color color =
        new java.awt.Color(partyColor.getRed(), partyColor.getGreen(), partyColor.getBlue());
    for (MatchPlayer teammate : p.getParty().getPlayers()) {
      if (p == teammate) continue;
      Location loc = teammate.getBukkit().getLocation();
      map.add(
          new TeamMemberLocation(
              teammate.getId(), color.getRGB(), loc.getX(), loc.getY(), loc.getZ()));
    }
    teamModule.sendLocations(p.getId(), map);
  }

  public void sendNoTeammates(MatchPlayer p) {
    if (p.getBukkit() == null) return;
    if (!p.getBukkit().isOnline()) return;
    teamModule.sendLocations(p.getId(), Collections.emptyList());
  }

  @EventHandler
  public void onPlayerLeaveParty(PlayerLeavePartyEvent e) {
    sendNoTeammates(e.getPlayer());
  }

  private void log(String s) {
    PGM.get().getLogger().info(s);
  }
}
