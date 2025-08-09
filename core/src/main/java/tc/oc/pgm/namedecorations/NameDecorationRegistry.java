package tc.oc.pgm.namedecorations;

import net.kyori.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import tc.oc.pgm.api.party.Party;
import tc.oc.pgm.api.player.MatchPlayer;

public interface NameDecorationRegistry extends Listener {

  /**
   * Get the fully decorated name for this player
   *
   * @param player The player to decorate
   * @param party The party this player is currently in
   * @return The name, decorated
   */
  String getDecoratedName(Player player, Party party);

  String getDecoratedNameWithoutFlair(Player player, Party party);

  /**
   * Get the message color of the player
   *
   * @param player
   * @return The color in string form e.g. "&c"
   */
  String getMessageColor(Player player);

  String getSuffix(Player player);

  /**
   * Get the fully decorated name as a Component
   *
   * <p>Note: Allows for prefix/suffix hover events
   *
   * @param player The player to decorate
   * @param party The party this player is currently in
   * @return The name, decorated, in component form
   */
  Component getDecoratedNameComponent(Player player, Party party);

  Component getDecoratedNameComponent(MatchPlayer player);

  /**
   * Set what name decoration provider this registry should use
   *
   * @param provider The name decoration provider to use
   */
  void setProvider(NameDecorationProvider provider);

  NameDecorationProvider getProvider();
}
