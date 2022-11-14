package tc.oc.pgm.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import tc.oc.pgm.api.player.MatchPlayer;

public class KillstreakEvent extends Event {
  private final MatchPlayer player;
  private final int amount;

  public KillstreakEvent(MatchPlayer player, int amount) {
    this.player = player;
    this.amount = amount;
  }

  public MatchPlayer getPlayer() {
    return player;
  }

  public int getAmount() {
    return amount;
  }

  // Bukkit event junk
  public static final HandlerList handlers = new HandlerList();

  @Override
  public HandlerList getHandlers() {
    return handlers;
  }

  public static HandlerList getHandlerList() {
    return handlers;
  }
}
