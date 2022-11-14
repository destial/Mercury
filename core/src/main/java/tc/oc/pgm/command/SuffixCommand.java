package tc.oc.pgm.command;

import app.ashcon.intake.Command;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.Permissions;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.PlayerData;
import tc.oc.pgm.modules.SuffixMatchModule;
import tc.oc.pgm.util.chat.Audience;
import tc.oc.pgm.util.text.TextException;

public class SuffixCommand {
  @Command(
      aliases = {"suffix", "suffixes"},
      desc = "Open the suffixes shop")
  public void suffix(MatchPlayer player) {
    if (!player.isObserving()) {
      player.sendWarning(TextComponent.of("You can only do that when you are observing!"));
      return;
    }
    SuffixMatchModule module = player.getMatch().needModule(SuffixMatchModule.class);
    module.display(player);
  }

  @Command(
      aliases = {"mysuffix", "mysuffixes"},
      desc = "Open your owned suffixes")
  public void mysuffix(MatchPlayer player) {
    if (!player.isObserving()) {
      player.sendWarning(TextComponent.of("You can only do that when you are observing!"));
      return;
    }
    SuffixMatchModule module = player.getMatch().needModule(SuffixMatchModule.class);
    module.displaySelf(player);
  }

  @Command(
      aliases = {"givesuffix"},
      desc = "Gives a suffix to a player",
      usage = "[player] [suffix]",
      perms = Permissions.DEV)
  public void givesuffix(CommandSender console, Match match, String name, String suffixName) {
    match.needModule(SuffixMatchModule.class);
    Audience audience = Audience.get(console);
    if (console instanceof ConsoleCommandSender) {
      Player player = Bukkit.getPlayer(name);
      UUID uuid =
          player != null && player.isOnline()
              ? player.getUniqueId()
              : UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes());
      PlayerData data = PGM.get().getDatastore().getPlayerData(uuid);
      if (data == null) throw TextException.of("command.playerNotFound");
      List<SuffixMatchModule.Suffix> suffixes =
          SuffixMatchModule.ALL_SUFFIXES.stream()
              .filter(s -> s.getId().startsWith(suffixName.toLowerCase()))
              .collect(Collectors.toList());
      if (suffixes.isEmpty()) {
        audience.sendWarning(
            TextComponent.of("Unable to find any suffixes that matches: " + suffixName));
        return;
      }
      SuffixMatchModule.Suffix first = suffixes.get(0);
      String only = ChatColor.stripColor(first.getFlair().getSuffix()).trim();
      suffixes.removeIf(s -> !ChatColor.stripColor(s.getFlair().getSuffix()).trim().equals(only));
      SuffixMatchModule.giveSuffixes(data, suffixes);
      audience.sendMessage(
          TextComponent.of(
              "Successfully gave "
                  + (player != null && player.isOnline() ? player.getName() : name)
                  + " the "
                  + only
                  + " suffixes!",
              TextColor.GREEN));
      match.sendMessage(
          TextComponent.of("[STORE] ", TextColor.GREEN)
              .append(
                  TextComponent.of(name + " has bought the " + only + " suffix!", TextColor.GOLD)));
      if (player != null && player.isOnline()) {
        Audience.get(player)
            .sendMessage(
                TextComponent.of(
                    "You have just received the " + only + " suffix! Check your suffixes!",
                    TextColor.GREEN));
      }
      return;
    }
    audience.sendWarning(TextComponent.of("You can only run this from console!"));
  }
}
