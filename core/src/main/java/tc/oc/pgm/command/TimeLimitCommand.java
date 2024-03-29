package tc.oc.pgm.command;

import app.ashcon.intake.Command;
import java.time.Duration;
import javax.annotation.Nullable;
import net.kyori.text.TextComponent;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.format.TextColor;
import tc.oc.pgm.api.Permissions;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.party.VictoryCondition;
import tc.oc.pgm.timelimit.TimeLimit;
import tc.oc.pgm.timelimit.TimeLimitMatchModule;
import tc.oc.pgm.timelimit.events.TimeLimitChangeEvent;
import tc.oc.pgm.util.TimeUtils;
import tc.oc.pgm.util.chat.Audience;

public final class TimeLimitCommand {

  @Command(
      aliases = {"timelimit", "tl"},
      desc = "Start a time limit",
      usage = "duration [result] [overtime] [max-overtime]",
      help = "Result can be 'default', 'objectives', 'tie', or the name of a team",
      flags = "r",
      perms = Permissions.STAFF)
  public void timelimit(
      Audience audience,
      Match match,
      Duration duration,
      @Nullable VictoryCondition result,
      @Nullable Duration overtime,
      @Nullable Duration maxOvertime) {

    try {
      final TimeLimitMatchModule time = match.needModule(TimeLimitMatchModule.class);
      time.cancel();
      time.setTimeLimit(
          new TimeLimit(
              null,
              duration.isNegative() ? Duration.ZERO : duration,
              overtime,
              maxOvertime,
              result,
              true));
      time.start();
      match.callEvent(new TimeLimitChangeEvent(match));

      audience.sendMessage(
          TranslatableComponent.of(
              "match.timeLimit.commandOutput",
              TextColor.YELLOW,
              TextComponent.of(TimeUtils.formatDuration(duration), TextColor.AQUA),
              result != null
                  ? result.getDescription(match)
                  : TranslatableComponent.of("misc.unknown")));
    } catch (Exception e) {
      audience.sendWarning(TranslatableComponent.of("command.moduleNotFound", TextColor.RED));
    }
  }
}
