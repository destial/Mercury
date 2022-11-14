package tc.oc.pgm.timelimit.events;

import org.bukkit.event.HandlerList;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.event.MatchEvent;

public class TimeLimitChangeEvent extends MatchEvent {
  public TimeLimitChangeEvent(Match match) {
    super(match);
  }

  private static final HandlerList handlers = new HandlerList();

  @Override
  public HandlerList getHandlers() {
    return handlers;
  }

  public static HandlerList getHandlerList() {
    return handlers;
  }
}
