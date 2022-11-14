package tc.oc.pgm.cycle;

import java.time.Duration;
import javax.annotation.Nullable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.map.MapOrder;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchModule;
import tc.oc.pgm.api.match.MatchScope;
import tc.oc.pgm.api.match.event.MatchFinishEvent;
import tc.oc.pgm.events.CycleStartEvent;
import tc.oc.pgm.events.ListenerScope;
import tc.oc.pgm.events.PlayerPartyChangeEvent;
import tc.oc.pgm.restart.RestartManager;

@ListenerScope(MatchScope.LOADED)
public class CycleMatchModule implements MatchModule, Listener {

  private final MapOrder mapOrder;
  private final Match match;
  private boolean called;

  public CycleMatchModule(Match match) {
    this.match = match;
    this.mapOrder = PGM.get().getMapOrder();
    called = false;
  }

  public void cycleNow() {
    startCountdown(Duration.ZERO);
  }

  public void startCountdown(@Nullable Duration duration) {
    if (duration == null) duration = PGM.get().getConfiguration().getCycleTime();
    // In case the cycle config is set to -1 used to disable autocycle
    if (duration.isNegative()) duration = Duration.ofSeconds(30);
    match.finish();
    match.getCountdown().start(new CycleCountdown(match), duration);
    if (!called) {
      match.callEvent(new CycleStartEvent(match, duration));
      called = true;
    } else {
      called = false;
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPartyChange(PlayerPartyChangeEvent event) {
    if (event.wasParticipating()
        && match.isRunning()
        && match.getParticipants().size() < PGM.get().getConfiguration().getMinimumPlayers()) {
      match.getLogger().info("Cycling due to empty match");
      match.finish();
      startCountdown(null);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onMatchEnd(MatchFinishEvent event) {
    final Match match = event.getMatch();
    mapOrder.matchEnded(match);

    if (!RestartManager.isQueued()) {
      Duration duration = PGM.get().getConfiguration().getCycleTime();
      if (!duration.isNegative()) {
        startCountdown(duration);
      }
    }
  }

  public boolean isCalled() {
    return called;
  }
}
