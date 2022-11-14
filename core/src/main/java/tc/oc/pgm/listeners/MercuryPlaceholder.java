package tc.oc.pgm.listeners;

import java.util.Iterator;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.event.MatchLoadEvent;
import tc.oc.pgm.api.party.Party;
import tc.oc.pgm.api.player.MatchPlayer;

public class MercuryPlaceholder extends PlaceholderExpansion implements Listener {
  private Match match;

  @Override
  public @NotNull String getIdentifier() {
    return "mercury";
  }

  @Override
  public @NotNull String getAuthor() {
    return "destiall";
  }

  @Override
  public @NotNull String getVersion() {
    return "1.0";
  }

  @Override
  public String onRequest(OfflinePlayer player, @NotNull String params) {
    if (match == null) {
      Iterator<Match> iterator = PGM.get().getMatchManager().getMatches();
      match = iterator.hasNext() ? iterator.next() : null;
    }

    if (params.equalsIgnoreCase("match")) {
      if (match == null) return "No match";
      return match.getMap().getName();
    }

    if (params.equalsIgnoreCase("team")) {
      if (player == null || match == null || !player.isOnline()) return "No team";
      MatchPlayer matchPlayer = match.getPlayer(player.getUniqueId());
      if (matchPlayer == null) return "No team";
      Party party = matchPlayer.getParty();
      if (party == null) return "No team";
      return party.getColor() + party.getNameLegacy();
    }

    return null;
  }

  @EventHandler
  public void onMatchLoad(MatchLoadEvent e) {
    match = e.getMatch();
  }

  @Override
  public boolean register() {
    Bukkit.getPluginManager().registerEvents(this, PGM.get());
    return super.register();
  }
}
