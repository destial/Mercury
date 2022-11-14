package tc.oc.pgm.command;

import app.ashcon.intake.Command;
import app.ashcon.intake.bukkit.parametric.Type;
import app.ashcon.intake.bukkit.parametric.annotation.Fallback;
import app.ashcon.intake.parametric.annotation.Default;
import app.ashcon.intake.parametric.annotation.Text;
import java.time.Duration;
import java.util.UUID;
import javax.annotation.Nullable;
import net.kyori.text.TextComponent;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.json.JSONArray;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.Permissions;
import tc.oc.pgm.api.coins.Coins;
import tc.oc.pgm.api.map.MapInfo;
import tc.oc.pgm.api.map.MapOrder;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.cycle.CycleMatchModule;
import tc.oc.pgm.restart.RestartManager;
import tc.oc.pgm.start.StartCountdown;
import tc.oc.pgm.start.StartMatchModule;
import tc.oc.pgm.start.UnreadyReason;
import tc.oc.pgm.util.chat.Audience;
import tc.oc.pgm.util.text.TextException;

public final class CoinCommand implements Listener {

  @Command(
      aliases = {"coins", "bal", "balance", "money"},
      desc = "Show how many coins you have",
      usage = "<player>")
  public void coins(Match match, MatchPlayer sender, @Fallback(Type.NULL) @Nullable Player player) {
    MatchPlayer affected = sender;
    if (player != null) {
      affected = match.getPlayer(player);
    }
    if (affected == null) throw TextException.of("command.playerNotFound");
    sender.sendMessage(
        TranslatableComponent.of(
            "coins.get.other", TextColor.YELLOW, TextComponent.of(affected.getCoins().getCoins())));
  }

  @Command(
      aliases = {"pay"},
      desc = "Pay a user coins",
      usage = "<player>")
  public void pay(MatchPlayer player, Player affected, @Default("1") int amount) {
    MatchPlayer affectedPlayer = player.getMatch().getPlayer(affected);
    if (affectedPlayer == null) throw TextException.of("command.playerNotFound");
    if (amount <= 0) {
      player.sendWarning(TextComponent.of("Cannot pay negative coins!"));
      return;
    }
    if (amount > player.getCoins().getCoins()) {
      player.sendWarning(
          TextComponent.of(
              "Insufficient balance! You have " + player.getCoins().getCoins() + " coins!"));
      return;
    }
    player.getCoins().removeCoins(amount);
    affectedPlayer.getCoins().addCoins(amount);
    player.sendMessage(
        TextComponent.of("You have sent " + amount + " coins to ", TextColor.YELLOW)
            .append(affectedPlayer.getName()));
    affectedPlayer.sendMessage(
        TextComponent.of("You have received " + amount + " coins from ", TextColor.YELLOW)
            .append(player.getName()));
  }

  @Command(
      aliases = {"buymap"},
      desc = "Buy a map to cycle to next")
  public void buymap(MatchPlayer sender, @Text MapInfo map, MapOrder mapOrder, Match match) {
    if (Bukkit.getPluginManager().getPlugin("Events") != null) {
      sender.sendWarning(TextComponent.of("Coins are currently disabled!"));
      return;
    }
    if (sender.getCoins().getCoins() < 10000) {
      sender.sendWarning(
          TextComponent.of(
              "You do not have enough coins! 10,000 coins is needed to buy a map cycle!",
              TextColor.RED));
      return;
    }
    if (mapOrder.getNextMap() != null) {
      sender.sendWarning(TextComponent.of("There is already a map set after this!"));
      return;
    }
    if (RestartManager.isQueued()) {
      sender.sendWarning(TextComponent.of("Server is restarting!"));
      return;
    }
    if (map == match.getMap()) {
      sender.sendWarning(TextComponent.of("You cannot set the same map as the current!"));
      return;
    }

    sender.getCoins().removeCoins(10000);
    mapOrder.setNextMap(map);
    sender.sendMessage(
        TextComponent.of("You have set the next map to be: " + map.getName(), TextColor.GREEN));
    match.sendMessage(
        TextComponent.of(
            sender.getNameLegacy()
                + " has bought a map cycle to "
                + map.getName()
                + " for 10,000 coins!",
            TextColor.AQUA));
  }

  @Command(
      aliases = {"buyskip"},
      desc = "Buy a map skip of the current map")
  public void buyskip(MatchPlayer sender, Match match, MapOrder mapOrder) {
    if (Bukkit.getPluginManager().getPlugin("Events") != null) {
      sender.sendWarning(TextComponent.of("Coins are currently disabled!"));
      return;
    }
    if (match.isRunning()) {
      sender.sendWarning(TranslatableComponent.of("admin.start.matchRunning"));
      return;
    } else if (match.isFinished()) {
      sender.sendWarning(TranslatableComponent.of("admin.start.matchFinished"));
      return;
    }
    if (sender.getCoins().getCoins() < 10000) {
      sender.sendWarning(
          TextComponent.of(
              "You do not have enough coins! 10,000 coins is needed to buy a map skip!"));
      return;
    }
    if (mapOrder.getNextMap() != null) {
      sender.sendWarning(TextComponent.of("There is already a map set after this!"));
      return;
    }
    if (match.needModule(CycleMatchModule.class).isCalled()) {
      sender.sendWarning(TranslatableComponent.of("admin.start.matchFinished"));
      return;
    }
    if (RestartManager.isQueued()) {
      sender.sendWarning(TextComponent.of("Server is restarting!"));
      return;
    }

    sender.getCoins().removeCoins(10000);
    if (mapOrder.getNextMap() != match.getMap()) {
      mapOrder.setNextMap(mapOrder.getNextMap());
    }
    match.needModule(CycleMatchModule.class).startCountdown(null);
    sender.sendMessage(TextComponent.of("You have skipped this map!", TextColor.GREEN));
    match.sendMessage(
        TextComponent.of(
            sender.getNameLegacy() + " has bought a map skip for 10,000 coins!", TextColor.AQUA));
  }

  @Command(
      aliases = {"buystart"},
      desc = "Instantly start the current map")
  public void buystart(MatchPlayer sender, Match match) {
    if (Bukkit.getPluginManager().getPlugin("Events") != null) {
      sender.sendWarning(TextComponent.of("Coins are currently disabled!"));
      return;
    }

    if (match.isRunning()) {
      sender.sendWarning(TranslatableComponent.of("admin.start.matchRunning"));
      return;
    } else if (match.isFinished()) {
      sender.sendWarning(TranslatableComponent.of("admin.start.matchFinished"));
      return;
    }

    if (sender.getCoins().getCoins() < 5000) {
      sender.sendWarning(
          TextComponent.of(
              "You do not have enough coins! 5,000 coins is needed to buy an instant start!"));
      return;
    }

    final StartMatchModule start = match.needModule(StartMatchModule.class);
    if (!start.canStart(true)) {
      sender.sendWarning(TranslatableComponent.of("admin.start.unknownState"));
      for (UnreadyReason reason : start.getUnreadyReasons(true)) {
        sender.sendWarning(reason.getReason());
      }
      return;
    }
    sender.getCoins().removeCoins(5000);
    sender.sendMessage(TextComponent.of("You have started this match!", TextColor.GREEN));
    match.sendMessage(
        TextComponent.of(
            sender.getNameLegacy() + " has bought a map start for 5,000 coins!", TextColor.AQUA));
    match.getCountdown().cancelAll(StartCountdown.class);
    start.forceStartCountdown(Duration.ofSeconds(0), null);
  }

  @Command(
      aliases = {"setcoins", "setbal", "setbalance", "setmoney"},
      desc = "Sets the amount of coins",
      usage = "[player] [amount]",
      perms = Permissions.STAFF)
  public void setcoins(Audience sender, Player affected, @Default("1") int amount) {
    if (Bukkit.getPluginManager().getPlugin("Events") != null) {
      sender.sendWarning(TextComponent.of("Coins are currently disabled!"));
      return;
    }

    Match match = PGM.get().getMatchManager().getMatch(affected.getWorld());
    if (match == null) throw TextException.of("command.unknownError.user");
    MatchPlayer a = match.getPlayer(affected);
    if (a == null) throw TextException.of("command.playerNotFound");
    Coins coins = a.getCoins();
    coins.setCoins(amount);
    sender.sendMessage(
        TranslatableComponent.of(
            "coins.set", TextColor.YELLOW, a.getName(), TextComponent.of(coins.getCoins())));
    a.sendMessage(
        TranslatableComponent.of(
            "death.getcoins",
            TextColor.YELLOW,
            TextComponent.of(coins.getCoins(), TextColor.GOLD),
            TextComponent.of("given")));
  }

  @Command(
      aliases = {"givecoins", "givemoney", "addbalance", "addcoins", "addmoney", "addbal"},
      desc = "Gives coins to player",
      usage = "[player] [amount]",
      perms = Permissions.STAFF)
  public void givecoins(Audience sender, String affected, Match match, @Default("1") int amount) {
    if (Bukkit.getPluginManager().getPlugin("Events") != null) {
      sender.sendWarning(TextComponent.of("Coins are currently disabled!"));
      return;
    }
    Player p = Bukkit.getPlayer(affected);
    Coins coins =
        PGM.get()
            .getDatastore()
            .getCoins(
                p != null && p.isOnline()
                    ? p.getUniqueId()
                    : UUID.nameUUIDFromBytes(("OfflinePlayer:" + affected).getBytes()));
    if (coins == null) throw TextException.of("command.playerNotFound");
    coins.addCoins(amount);
    sender.sendMessage(
        TranslatableComponent.of(
            "coins.set",
            TextColor.YELLOW,
            TextComponent.of(p != null && p.isOnline() ? p.getName() : affected),
            TextComponent.of(coins.getCoins()),
            TextComponent.of("given")));
    if (p != null && p.isOnline()) {
      MatchPlayer mp = match.getPlayer(p);
      if (mp == null) return;
      mp.sendMessage(
          TranslatableComponent.of(
              "death.getcoins",
              TextColor.YELLOW,
              TextComponent.of(amount, TextColor.GOLD),
              TextComponent.of("given")));
    }
  }

  @Command(
      aliases = {"addtag"},
      desc = "Add a tag to a player",
      perms = Permissions.DEV)
  public void addtag(Match match, Audience audience, Player target, String tag) {
    MatchPlayer targetPlayer = match.getPlayer(target);
    if (targetPlayer == null) {
      audience.sendWarning(TextComponent.of("Player not found!"));
      return;
    }
    JSONArray tags;
    if (targetPlayer.getPlayerData().getData().has("tags")) {
      tags = targetPlayer.getPlayerData().getData().getJSONArray("tags");
    } else {
      tags = new JSONArray();
    }
    tags.put(tag);
    targetPlayer.getPlayerData().setData("tags", tags);
    audience.sendMessage(
        TextComponent.of("Added tag ", TextColor.YELLOW)
            .append(TextComponent.of(tag, TextColor.LIGHT_PURPLE))
            .append(TextComponent.of(" to ", TextColor.YELLOW).append(targetPlayer.getName())));
  }
}
