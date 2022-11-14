package tc.oc.pgm.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import tc.oc.pgm.api.map.MapInfo;

public class VoteCompleteEvent extends Event {
  private final MapInfo map;
  private final int votes;

  public VoteCompleteEvent(MapInfo map, int votes) {
    this.map = map;
    this.votes = votes;
  }

  public int getVotes() {
    return votes;
  }

  public MapInfo getVotedMap() {
    return map;
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
