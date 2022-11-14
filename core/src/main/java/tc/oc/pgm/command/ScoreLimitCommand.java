package tc.oc.pgm.command;

import app.ashcon.intake.Command;
import app.ashcon.intake.parametric.annotation.Switch;
import javax.annotation.Nullable;
import net.kyori.text.TextComponent;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.format.TextColor;
import tc.oc.pgm.api.Permissions;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.score.ScoreMatchModule;
import tc.oc.pgm.util.chat.Audience;

public final class ScoreLimitCommand {

  @Command(
      aliases = {"scorelimit", "sl"},
      desc = "Change the score limit of this match",
      usage = "[amount]",
      flags = "r",
      perms = Permissions.STAFF)
  public void scorelimit(
      Audience audience, Match match, @Nullable Integer amount, @Switch('f') boolean reset) {
    final ScoreMatchModule score = match.getModule(ScoreMatchModule.class);
    if (score == null) {
      audience.sendWarning(TranslatableComponent.of("command.moduleNotFound", TextColor.RED));
      return;
    }
    if (amount == null && reset) {
      score.setScoreLimit(null);
    } else if (amount != null) {
      if (amount <= 0) {
        score.setScoreLimit(null);
      } else {
        score.setScoreLimit(amount);
      }
    } else {
      audience.sendWarning(
          TranslatableComponent.of(
              "command.incorrectUsage",
              TextComponent.of("/scorelimit [amount] [-r]", TextColor.RED)));
      return;
    }

    audience.sendMessage(
        TranslatableComponent.of(
            "match.scoreLimit.commandOutput",
            TextColor.YELLOW,
            TextComponent.of(score.getScoreLimit(), TextColor.AQUA)));
  }
}
