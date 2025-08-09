package tc.oc.pgm.command;

import app.ashcon.intake.Command;
import app.ashcon.intake.bukkit.parametric.Type;
import app.ashcon.intake.bukkit.parametric.annotation.Fallback;
import java.util.UUID;
import javax.annotation.Nullable;
import net.kyori.text.TextComponent;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.format.TextColor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.Permissions;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.player.GlobalStats;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.modules.GlobalStatsMatchModule;
import tc.oc.pgm.util.LegacyFormatUtils;
import tc.oc.pgm.util.chat.Audience;
import tc.oc.pgm.util.named.NameStyle;
import tc.oc.pgm.util.text.TextException;
import tc.oc.pgm.util.text.TextFormatter;

public final class GlobalStatsCommand {

  @Command(
      aliases = {"globalstats", "gstats", "gs"},
      desc = "Show your stats globally",
      usage = "<player>")
  public void globalstats(
      Match match, MatchPlayer sender, @Fallback(Type.NULL) @Nullable Player player) {
    MatchPlayer other = sender;
    if (player != null) {
      other = sender.getMatch().getPlayer(player);
      if (other == null) throw TextException.of("command.playerNotFound");
    }
    GlobalStats stats = other.getStats();
    sender.sendMessage(
        TextFormatter.horizontalLineHeading(
            sender.getBukkit(),
            TranslatableComponent.of(
                "match.stats.other", TextColor.WHITE, other.getName(NameStyle.PLAIN)),
            TextColor.YELLOW));
    match.needModule(GlobalStatsMatchModule.class).sendGlobalStatsMessage(sender, stats);
  }

  @Command(
      aliases = {"clearstats", "resetstats"},
      desc = "Clear a player's stats",
      usage = "<player>",
      perms = Permissions.DEV)
  public void clearstats(Audience sender, String name) {
    UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes());
    PGM.get().getDatastore().clearStats(uuid);
    sender.sendMessage(TextComponent.of("Cleared stats of: " + name, TextColor.YELLOW));
  }

  @Command(
      aliases = {"topstats", "leaderboard", "ldb", "lb"},
      desc = "Show global stats",
      usage = "<top>")
  public void topstats(
      Audience sender, Match match, @Fallback(Type.NULL) @Nullable GlobalStats.Leaderboard type) {
    if (type == null) {
      sender.sendMessage(
          TextComponent.of(
              LegacyFormatUtils.horizontalLineHeading("Leaderboard", ChatColor.WHITE),
              TextColor.YELLOW));
      match.needModule(GlobalStatsMatchModule.class).sendTopGlobalStatsMessage(sender);
      return;
    }
    String tag = "Top Kills";
    switch (type) {
      case WINS:
        tag = "Top Wins";
        break;
      case BOW:
        tag = "Top Longest Shots";
        break;
      case KDR:
        tag = "Top Kills / Deaths Ratios";
        break;
      case MVP:
        tag = "Top MVPs";
        break;
      case CORES:
        tag = "Top Cores Leaked";
        break;
      case FLAGS:
        tag = "Top Flags Captured";
        break;
      case WOOLS:
        tag = "Top Wools Captured";
        break;
      case DEATHS:
        tag = "Top Deaths";
        break;
      case LOSSES:
        tag = "Top Losses";
        break;
      case MONUMENTS:
        tag = "Top Monuments Broken";
        break;
      case KILLSTREAK:
        tag = "Top Killstreaks";
        break;
      case WLR:
        tag = "Top Win / Lose Ratios";
        break;
      case ELO:
        tag = "Top Elo";
        break;
      case SCORE:
        tag = "Top Combat Score";
        break;
      case ARROWS:
        tag = "Top Arrows Hit";
        break;
      default:
        break;
    }
    sender.sendMessage(
        TextComponent.of(
            LegacyFormatUtils.horizontalLineHeading(tag, ChatColor.YELLOW), TextColor.WHITE));
    match.needModule(GlobalStatsMatchModule.class).sendTopGlobalStatsMessage(sender, type);
  }
}
