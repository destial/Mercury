package tc.oc.pgm.command;

import app.ashcon.intake.Command;
import app.ashcon.intake.bukkit.parametric.Type;
import app.ashcon.intake.bukkit.parametric.annotation.Fallback;
import javax.annotation.Nullable;
import net.kyori.text.TextComponent;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.format.TextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.util.text.TextException;

public final class EloCommand implements Listener {

  @Command(
      aliases = {"elo", "points"},
      desc = "Show your elo",
      usage = "<player>")
  public void elo(Match match, MatchPlayer sender, @Fallback(Type.NULL) @Nullable Player player) {
    MatchPlayer affected = sender;
    if (player != null) {
      affected = match.getPlayer(player);
    }
    if (affected == null) throw TextException.of("command.playerNotFound");
    sender.sendMessage(
        TextComponent.of(affected.getNameLegacy() + "'s ", TextColor.YELLOW)
            .append(
                TranslatableComponent.of(
                    "match.stats.elo",
                    TextColor.YELLOW,
                    TextComponent.of(affected.getStats().getElo(), TextColor.GREEN))));
  }
}
