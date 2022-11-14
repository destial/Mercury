package tc.oc.pgm.events;

import java.time.Duration;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import tc.oc.pgm.api.match.Match;

public class CycleStartEvent extends Event {
  private final Match match;
  private final Duration duration;

  public CycleStartEvent(Match match, Duration duration) {
    this.match = match;
    this.duration = duration;
  }

  public Duration getDuration() {
    return duration;
  }

  public Match getMatch() {
    return match;
  }

  private static final HandlerList handlers = new HandlerList();

  public static HandlerList getHandlerList() {
    return handlers;
  }

  @Override
  public HandlerList getHandlers() {
    return handlers;
  }
}
