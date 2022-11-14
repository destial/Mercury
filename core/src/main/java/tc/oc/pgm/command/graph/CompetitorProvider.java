package tc.oc.pgm.command.graph;

import app.ashcon.intake.argument.CommandArgs;
import app.ashcon.intake.argument.MissingArgumentException;
import app.ashcon.intake.bukkit.parametric.provider.BukkitProvider;
import app.ashcon.intake.parametric.ProvisionException;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.command.CommandSender;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.party.Competitor;
import tc.oc.pgm.util.StringUtils;
import tc.oc.pgm.util.text.TextException;

final class CompetitorProvider implements BukkitProvider<Competitor> {

  @Override
  public String getName() {
    return "team";
  }

  @Override
  public Competitor get(CommandSender sender, CommandArgs args, List<? extends Annotation> list)
      throws MissingArgumentException, ProvisionException {
    final String text = args.next();

    final Match match = PGM.get().getMatchManager().getMatch(sender);
    if (match == null) {
      throw TextException.of("command.onlyPlayers");
    }

    final Map<String, Competitor> byName = new HashMap<>();
    for (Competitor competitor : match.getCompetitors())
      byName.put(competitor.getNameLegacy(), competitor);

    final Competitor competitor = StringUtils.bestFuzzyMatch(text, byName, 0.9);

    if (competitor == null) {
      throw TextException.of("command.competitorNotFound");
    }

    return competitor;
  }
}
