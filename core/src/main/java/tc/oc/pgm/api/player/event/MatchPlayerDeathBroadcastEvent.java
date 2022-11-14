package tc.oc.pgm.api.player.event;

import static com.google.common.base.Preconditions.checkNotNull;

import net.kyori.text.Component;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.PlayerDeathEvent;
import tc.oc.pgm.api.player.MatchPlayer;

/** Called when {@link MatchPlayer} dies, the victim is the {@link #getPlayer()}. */
public class MatchPlayerDeathBroadcastEvent extends MatchPlayerEvent {

  private final MatchPlayerDeathEvent parent;
  private final Component message;

  public MatchPlayerDeathBroadcastEvent(MatchPlayerDeathEvent parent, Component message) {
    super(checkNotNull(parent.getVictim()));
    this.parent = checkNotNull(parent);
    this.message = message;
  }

  /**
   * Get the {@link org.bukkit.Bukkit} {@link PlayerDeathEvent}.
   *
   * @return The parent event.
   */
  public final MatchPlayerDeathEvent getParent() {
    return parent;
  }

  public final Component getMessage() {
    return message;
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
