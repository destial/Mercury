package tc.oc.pgm.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.event.NameDecorationChangeEvent;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.event.MatchLoadEvent;
import tc.oc.pgm.api.player.MatchPlayer;
import xyz.destiall.disguise.events.DisguiseEvent;
import xyz.destiall.disguise.events.UndisguiseEvent;

public class DisguiseListener implements Listener {
  private Match match;

  public DisguiseListener() {}

  @EventHandler
  public void onMatch(MatchLoadEvent e) {
    match = e.getMatch();
  }

  @EventHandler
  public void onDisguise(DisguiseEvent e) {
    MatchPlayer matchPlayer = match.getPlayer(e.getPlayer());
    if (matchPlayer == null) return;
    PGM.get().getVanishManager().setVanished(matchPlayer, true, true);
    match.callEvent(new NameDecorationChangeEvent(e.getPlayer().getUniqueId()));
  }

  @EventHandler
  public void onUndisguise(UndisguiseEvent e) {
    MatchPlayer matchPlayer = match.getPlayer(e.getPlayer().getUniqueId());
    if (matchPlayer == null) return;
    match.callEvent(new NameDecorationChangeEvent(e.getPlayer().getUniqueId()));
  }
}
