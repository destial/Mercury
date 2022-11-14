package tc.oc.pgm.command;

import app.ashcon.intake.Command;
import net.kyori.text.TextComponent;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.format.TextColor;
import tc.oc.pgm.api.Permissions;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.party.Competitor;
import tc.oc.pgm.api.party.Party;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.modules.EditKitMatchModule;

public final class EditKitCommand {
  @Command(
      aliases = {"editkit", "ek", "kit"},
      desc = "Edit the kit of the team",
      usage = "[team]",
      perms = Permissions.PREM)
  public void editkit(MatchPlayer player, Match match, Party team) {
    final EditKitMatchModule editKit = match.getModule(EditKitMatchModule.class);
    if (editKit == null) {
      player.sendWarning(TranslatableComponent.of("command.moduleNotFound", TextColor.RED));
      return;
    }
    if (!player.isObserving()) {
      player.sendWarning(
          TextComponent.of("You have to be in spectator mode to edit kits!", TextColor.RED));
      return;
    }
    if (team instanceof Competitor) {
      Competitor competitor = (Competitor) team;
      if (editKit.editKit(player, competitor))
        player.sendMessage(
            TextComponent.of("Type /savekit to save your kit after editing!", TextColor.GREEN));
    } else {
      player.sendWarning(TextComponent.of("This team's kit cannot be edited!", TextColor.RED));
    }
  }

  @Command(
      aliases = {"savekit", "sk"},
      desc = "Save the kit",
      perms = Permissions.PREM)
  public void savekit(MatchPlayer player, Match match) {
    final EditKitMatchModule editKit = match.getModule(EditKitMatchModule.class);
    if (editKit == null) {
      player.sendWarning(TranslatableComponent.of("command.moduleNotFound", TextColor.RED));
      return;
    }
    if (editKit.saveKit(player)) {
      player.sendMessage(TextComponent.of("Saved kit", TextColor.GREEN));
      return;
    }
    player.sendWarning(TextComponent.of("You are currently not editing a kit!", TextColor.RED));
  }
}
