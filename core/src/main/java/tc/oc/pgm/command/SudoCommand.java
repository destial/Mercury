package tc.oc.pgm.command;

import app.ashcon.intake.Command;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import tc.oc.pgm.api.Permissions;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.util.chat.Audience;

public final class SudoCommand {
  @Command(
      aliases = {"sudo,force"},
      desc = "Force a player to perform a command",
      perms = Permissions.DEV)
  public void sudo(Audience sender, MatchPlayer player, String command) {
    final String targetCommand = command.startsWith("/") ? command.substring(1) : command;
    player.getBukkit().performCommand(targetCommand);
    sender.sendMessage(
        TextComponent.of("Made " + player.getNameLegacy() + " perform command: ", TextColor.GREEN));
    sender.sendMessage(TextComponent.of("/" + targetCommand, TextColor.GOLD));
  }
}
