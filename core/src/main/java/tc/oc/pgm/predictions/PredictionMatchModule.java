package tc.oc.pgm.predictions;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.kyori.text.event.ClickEvent;
import net.kyori.text.event.HoverEvent;
import net.kyori.text.format.TextColor;
import net.kyori.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.coins.Coins;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchModule;
import tc.oc.pgm.api.match.MatchScope;
import tc.oc.pgm.api.match.event.MatchFinishEvent;
import tc.oc.pgm.api.match.event.MatchStartEvent;
import tc.oc.pgm.api.match.event.MatchUnloadEvent;
import tc.oc.pgm.api.party.Competitor;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.Username;
import tc.oc.pgm.events.ListenerScope;
import tc.oc.pgm.events.PlayerJoinMatchEvent;
import tc.oc.pgm.events.PlayerPartyChangeEvent;
import tc.oc.pgm.teams.TeamMatchModule;
import tc.oc.pgm.util.LegacyFormatUtils;
import tc.oc.pgm.util.nms.NMSHacks;
import tc.oc.pgm.util.text.TextTranslations;

@ListenerScope(MatchScope.LOADED)
public class PredictionMatchModule implements MatchModule, Listener {
  private static final String SYMBOL_IGNORE = "✕"; // ✕
  private static final String SYMBOL_VOTED = "✔"; // ✔
  private static final int VOTE_SLOT = 3;
  private static final int TIMER = 5;
  public static final int DEFAULT_BET = 250;

  private final Match match;
  private final Map<Competitor, Map<UUID, Integer>> predictions;
  private final Map<UUID, Integer> currentBetting;
  private boolean canPredict;
  private boolean gaveOut;

  public PredictionMatchModule(Match match) {
    this.match = match;
    predictions = new HashMap<>();
    currentBetting = new HashMap<>();
    canPredict = false;
    gaveOut = false;
    match
        .getExecutor(MatchScope.LOADED)
        .schedule(
            () -> {
              canPredict = match.hasModule(TeamMatchModule.class);
              if (canPredict) {
                for (Competitor competitor : match.getCompetitors()) {
                  predictions.put(competitor, new HashMap<>());
                }
                match.getPlayers().forEach(viewer -> sendBook(viewer, true));
              }
            },
            5,
            TimeUnit.SECONDS);
  }

  @EventHandler
  public void onMatchStart(MatchStartEvent e) {
    if (e.getMatch() != match) return;
    if (!canPredict) return;
    match.sendMessage(
        TextComponent.of(
            "Predictions will close in " + TIMER + " seconds!", TextColor.LIGHT_PURPLE));
    match
        .getExecutor(MatchScope.LOADED)
        .schedule(
            () -> {
              canPredict = false;
              match.sendMessage(
                  TextComponent.of("Predictions are now closed!", TextColor.LIGHT_PURPLE));
              match.sendMessage(
                  TextComponent.of(
                      "A total of " + getTotalPool() + " coins have been bet!",
                      TextColor.LIGHT_PURPLE));
            },
            TIMER,
            TimeUnit.SECONDS);
  }

  @EventHandler
  public void onJoinMatch(PlayerJoinMatchEvent e) {
    if (!canPredict) return;
    match
        .getExecutor(MatchScope.LOADED)
        .schedule(
            () -> {
              if (e.getPlayer().getParty() instanceof Competitor) return;
              sendBook(e.getPlayer(), false);
            },
            1,
            TimeUnit.SECONDS);
  }

  @EventHandler
  public void onPartyChange(PlayerPartyChangeEvent e) {
    if (e.getNewParty() instanceof Competitor) {
      for (Map<UUID, Integer> votes : predictions.values()) {
        if (votes.containsKey(e.getPlayer().getId())) {
          Coins coins = e.getPlayer().getCoins();
          coins.addCoins(votes.remove(e.getPlayer().getId()));
          e.getPlayer()
              .sendWarning(
                  TextComponent.of("Your prediction has been removed because you joined!"));
        }
      }
    }
  }

  @EventHandler
  public void onMatchEnd(MatchFinishEvent e) {
    if (e.getMatch() != match) return;
    if (!match.hasModule(TeamMatchModule.class)) return;
    match
        .getExecutor(MatchScope.LOADED)
        .schedule(
            () -> {
              gaveOut = true;
              Competitor competitor = e.getWinner();
              if (competitor != null) {
                Map.Entry<UUID, Integer> most = getMostEarnedPlayer(competitor);
                if (most != null) {
                  Username username = PGM.get().getDatastore().getUsername(most.getKey());
                  match.sendMessage(
                      TextComponent.of(
                          username.getNameLegacy()
                              + " won the most with "
                              + most.getValue() * 2
                              + " coins from prediction!",
                          TextColor.GOLD));
                }
                for (Map.Entry<Competitor, Map<UUID, Integer>> entry : predictions.entrySet()) {
                  boolean win = entry.getKey() == competitor;
                  for (Map.Entry<UUID, Integer> predict : entry.getValue().entrySet()) {
                    MatchPlayer player = match.getPlayer(predict.getKey());
                    if (player == null) continue;
                    announceWinner(player, predict.getValue(), win);
                  }
                }
              }
            },
            3L,
            TimeUnit.SECONDS);
  }

  @EventHandler
  public void onUnload(MatchUnloadEvent e) {
    if (gaveOut) return;
    for (Map<UUID, Integer> bets : predictions.values()) {
      for (Map.Entry<UUID, Integer> bet : bets.entrySet()) {
        Coins coins = PGM.get().getDatastore().getCoins(bet.getKey());
        coins.addCoins(bet.getValue());
      }
    }
  }

  public void announceWinner(MatchPlayer viewer, int bet, boolean win) {
    viewer.sendMessage(
        LegacyFormatUtils.horizontalDivider(
            (win ? ChatColor.GREEN : ChatColor.RED).asBungee(), 100));
    int amount = bet * 2;
    if (win) {
      viewer.sendMessage(
          TextComponent.of(
              "You predicted correctly! You have gained " + amount + " coins!", TextColor.GREEN));
      viewer.getCoins().addCoins(amount);
    } else {
      amount *= 2;
      viewer.sendMessage(
          TextComponent.of(
              "You predicted poorly! You have lost " + amount + " coins!", TextColor.RED));
      viewer.getCoins().removeCoins(amount);
    }
    viewer.sendMessage(
        LegacyFormatUtils.horizontalDivider(
            (win ? ChatColor.GREEN : ChatColor.RED).asBungee(), 100));
  }

  public boolean canPredict() {
    return canPredict;
  }

  public void sendBook(MatchPlayer viewer, boolean forceOpen) {
    if (!canPredict || viewer.isLegacy() || viewer.isParticipating()) return;

    Integer betting = currentBetting.get(viewer.getId());
    if (betting == null) {
      betting = DEFAULT_BET;
      currentBetting.put(viewer.getId(), DEFAULT_BET);
    }

    TextComponent.Builder content = TextComponent.builder();
    content.append("Which team will win?", TextColor.BLUE);
    content
        .append(TextComponent.newline())
        .append(LegacyFormatUtils.horizontalDivider(ChatColor.DARK_PURPLE.asBungee(), 60));

    boolean hasVoted = false;
    for (Competitor competitor : predictions.keySet()) {
      if (predictions.get(competitor).containsKey(viewer.getId())) hasVoted = true;
      content.append(TextComponent.newline()).append(getCompetitorComponent(viewer, competitor));
    }

    content
        .append(TextComponent.newline())
        .append(LegacyFormatUtils.horizontalDivider(ChatColor.DARK_PURPLE.asBungee(), 60));

    content.append(
        TextComponent.newline().append(TextComponent.of("Betting: " + betting, TextColor.GOLD)));

    if (!hasVoted) {
      content
          .append(TextComponent.newline())
          .append(TextComponent.newline())
          .append(getBetDecreaseComponent(10))
          .append(getBetDecreaseComponent(50))
          .append(getBetDecreaseComponent(100))
          .append(TextComponent.newline())
          .append(getBetIncreaseComponent(10))
          .append(getBetIncreaseComponent(50))
          .append(getBetIncreaseComponent(100));
    }

    content
        .append(TextComponent.newline())
        .append(TextComponent.of("Win Rate: x2", TextColor.GREEN))
        .append(TextComponent.of("Lose Rate: x4", TextColor.RED));

    ItemStack is = new ItemStack(Material.WRITTEN_BOOK);
    BookMeta meta = (BookMeta) is.getItemMeta();
    meta.setAuthor("PGM");

    String title = ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Predictions";
    meta.setTitle(title);

    NMSHacks.setBookPages(
        meta, TextTranslations.toBaseComponent(content.build(), viewer.getBukkit()));
    is.setItemMeta(meta);

    ItemStack held = viewer.getInventory().getItemInHand();
    if (held.getType() != Material.WRITTEN_BOOK
        || !title.equals(((BookMeta) is.getItemMeta()).getTitle())) {
      viewer.getInventory().setHeldItemSlot(VOTE_SLOT);
    }
    viewer.getInventory().setItemInHand(is);
    if (forceOpen) NMSHacks.openBook(is, viewer.getBukkit());
  }

  private Map.Entry<UUID, Integer> getMostEarnedPlayer(Competitor winner) {
    Optional<Map.Entry<UUID, Integer>> earned =
        predictions.get(winner).entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .findFirst();
    Map.Entry<UUID, Integer> e = earned.orElse(null);
    if (e == null || e.getValue() <= 0) return null;
    return e;
  }

  public boolean toggleVote(Competitor competitor, UUID player) {
    Map<UUID, Integer> votes = this.predictions.get(competitor);
    if (votes == null) {
      votes = new HashMap<>();
      predictions.put(competitor, votes);
    }
    int bet = currentBetting.get(player);
    Coins coins = PGM.get().getDatastore().getCoins(player);
    if (votes.containsKey(player)) {
      coins.addCoins(votes.remove(player));
      return false;
    }
    votes.put(player, bet);
    coins.removeCoins(bet);
    for (Map.Entry<Competitor, Map<UUID, Integer>> entry : predictions.entrySet()) {
      if (entry.getKey() == competitor) continue;
      if (entry.getValue().containsKey(player)) {
        coins.addCoins(entry.getValue().remove(player));
      }
    }
    return true;
  }

  private Integer getTotalPool() {
    return predictions.values().stream()
        .mapToInt(p -> p.values().stream().mapToInt(a -> a).sum())
        .sum();
  }

  public boolean changeBet(UUID uuid, int amount) {
    Integer current = currentBetting.get(uuid);
    int newAmount = (current == null ? DEFAULT_BET : current) + amount;
    if (current == null) {
      currentBetting.put(uuid, DEFAULT_BET + amount);
    } else {
      currentBetting.put(uuid, current + amount);
    }
    if (newAmount < DEFAULT_BET) return false;
    currentBetting.put(uuid, newAmount);
    return true;
  }

  private Component getBetIncreaseComponent(int amount) {
    return TextComponent.builder()
        .append("[", TextColor.GOLD)
        .append("+" + amount, TextColor.GREEN)
        .append("] ", TextColor.GOLD)
        .clickEvent(ClickEvent.runCommand("/betchange -o " + amount))
        .hoverEvent(
            HoverEvent.showText(TextComponent.of("Add " + amount + " coins", TextColor.GREEN)))
        .build();
  }

  private Component getBetDecreaseComponent(int amount) {
    return TextComponent.builder()
        .append("[", TextColor.GOLD)
        .append("-" + amount, TextColor.RED)
        .append("] ", TextColor.GOLD)
        .clickEvent(ClickEvent.runCommand("/betchange -o -" + amount))
        .hoverEvent(
            HoverEvent.showText(TextComponent.of("Remove " + amount + " coins", TextColor.RED)))
        .build();
  }

  private Component getCompetitorComponent(MatchPlayer viewer, Competitor competitor) {
    boolean voted = predictions.get(competitor).containsKey(viewer.getId());
    return TextComponent.builder()
        .append(
            voted ? SYMBOL_VOTED : SYMBOL_IGNORE, voted ? TextColor.DARK_GREEN : TextColor.DARK_RED)
        .append(
            TextComponent.of(" ").decoration(TextDecoration.BOLD, !voted)) // Fix 1px symbol diff
        .append(competitor.getName().decoration(TextDecoration.BOLD, voted))
        .clickEvent(ClickEvent.runCommand("/predict -o " + competitor.getNameLegacy()))
        .hoverEvent(
            HoverEvent.showText(
                TextComponent.of(voted ? "Remove bet from " : "Bet on ", TextColor.GOLD)
                    .append(competitor.getName())))
        .build();
  }
}
