package tc.oc.pgm.tablist;

import org.bukkit.event.HandlerList;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.event.MatchEvent;

public class MatchTabInvalidateEvent extends MatchEvent {

  public MatchTabInvalidateEvent(Match match) {
    super(match);
  }

  private static final HandlerList handlerList = new HandlerList();

  @Override
  public HandlerList getHandlers() {
    return handlerList;
  }

  public static HandlerList getHandlerList() {
    return handlerList;
  }
}
