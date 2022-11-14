package tc.oc.pgm.community.events;

import java.util.Set;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.community.modules.CPSMatchModule;

public class CPSStopEvent extends Event {
  private final CPSMatchModule.CountedPlayer player;
  private static final HandlerList handlers = new HandlerList();

  public CPSStopEvent(CPSMatchModule.CountedPlayer player) {
    super();
    this.player = player;
  }

  public CPSMatchModule.CountedPlayer getCheckedPlayer() {
    return player;
  }

  public MatchPlayer getMatchPlayer() {
    return player.getPlayer();
  }

  public Set<MatchPlayer> getCheckers() {
    return player.getCheckers();
  }

  @Override
  public HandlerList getHandlers() {
    return handlers;
  }

  public static HandlerList getHandlerList() {
    return handlers;
  }
}
