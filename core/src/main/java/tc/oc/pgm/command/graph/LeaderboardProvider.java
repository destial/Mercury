package tc.oc.pgm.command.graph;

import app.ashcon.intake.argument.ArgumentException;
import app.ashcon.intake.argument.CommandArgs;
import app.ashcon.intake.argument.Namespace;
import app.ashcon.intake.bukkit.parametric.provider.BukkitProvider;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.CommandSender;
import tc.oc.pgm.api.player.GlobalStats;
import tc.oc.pgm.util.text.TextException;

public class LeaderboardProvider implements BukkitProvider<GlobalStats.Leaderboard> {
  @Override
  public String getName() {
    return "leaderboard";
  }

  @Override
  public GlobalStats.Leaderboard get(
      CommandSender sender, CommandArgs args, List<? extends Annotation> annotations)
      throws ArgumentException {
    final String query = args.next().toLowerCase();

    for (GlobalStats.Leaderboard settingKey : GlobalStats.Leaderboard.values()) {
      if (settingKey.name().equalsIgnoreCase(query)) {
        return settingKey;
      }
    }

    throw TextException.invalidFormat(query, GlobalStats.Leaderboard.class, null);
  }

  @Override
  public List<String> getSuggestions(
      String prefix, Namespace namespace, List<? extends Annotation> modifiers) {
    final String query = prefix.toLowerCase();
    final List<String> suggestions = new ArrayList<>();

    for (GlobalStats.Leaderboard settingKey : GlobalStats.Leaderboard.values()) {
      String l = settingKey.name().toLowerCase();
      if (l.startsWith(query)) {
        suggestions.add(l);
      }
    }

    return suggestions;
  }
}
