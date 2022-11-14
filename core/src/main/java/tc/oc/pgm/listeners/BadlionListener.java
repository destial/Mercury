package tc.oc.pgm.listeners;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import net.badlion.timers.api.Timer;
import net.badlion.timers.api.TimerApi;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.event.MatchFinishEvent;
import tc.oc.pgm.api.match.event.MatchStartEvent;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.event.MatchPlayerAddEvent;
import tc.oc.pgm.timelimit.TimeLimitMatchModule;
import tc.oc.pgm.timelimit.events.TimeLimitChangeEvent;

public class BadlionListener implements Listener {
  private final TimerApi api;
  private Timer timer;

  public BadlionListener() {
    PGM.get().getLogger().info("Found BadlionClientTimerAPI! Hooking into it!");
    api = TimerApi.getInstance();
  }

  @EventHandler
  public void onMatchStart(MatchStartEvent e) {
    Match match = e.getMatch();
    TimeLimitMatchModule tm = match.getModule(TimeLimitMatchModule.class);
    if (tm == null) return;
    Duration remaining = tm.getFinalRemaining();
    if (remaining == null) return;
    timer =
        api.createTimeTimer(
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
  public void onMatchEnd(MatchFinishEvent e) {
    if (timer != null) {
      api.removeTimer(timer);
      timer = null;
    }
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
      api.removeTimer(timer);
    }
    timer =
        api.createTimeTimer(
            "Time Remaining",
            new ItemStack(Material.WATCH),
            false,
            remaining.getSeconds(),
            TimeUnit.SECONDS);
    match.getPlayers().forEach(p -> timer.addReceiver(p.getBukkit()));
  }
}
