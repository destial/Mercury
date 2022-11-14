package tc.oc.pgm.command;

import static tc.oc.pgm.predictions.PredictionMatchModule.DEFAULT_BET;

import app.ashcon.intake.Command;
import app.ashcon.intake.parametric.annotation.Default;
import app.ashcon.intake.parametric.annotation.Switch;
import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.format.TextColor;
import tc.oc.pgm.api.coins.Coins;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.party.Competitor;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.predictions.PredictionMatchModule;

public final class PredictionCommand {

  @Command(
      aliases = {"predict"},
      desc = "Predict who will win",
      usage = "[competitor] [amount]")
  public void predict(
      MatchPlayer player,
      Competitor competitor,
      @Switch('o') boolean forceOpen,
      @Default("" + DEFAULT_BET) int bet) {
    Match match = player.getMatch();
    if (player.getParty() instanceof Competitor) {
      player.sendWarning(TextComponent.of("You cannot predict if you are playing!"));
      return;
    }
    PredictionMatchModule pmm = match.needModule(PredictionMatchModule.class);
    if (!pmm.canPredict()) {
      player.sendWarning(TextComponent.of("Predictions have closed!"));
      return;
    }
    if (bet < DEFAULT_BET) {
      player.sendWarning(TextComponent.of("You must bet a minimum of 500 coins!"));
      return;
    }
    Coins coins = player.getCoins();
    if (bet > coins.getCoins()) {
      player.sendWarning(
          TextComponent.of("You do not have enough coins to bet " + bet + " coins!"));
      return;
    }
    boolean voteResult = pmm.toggleVote(competitor, player.getId());
    Component voteAction =
        TranslatableComponent.of(
            voteResult ? "vote.for" : "vote.abstain",
            voteResult ? TextColor.GREEN : TextColor.RED,
            competitor.getName());
    player.sendMessage(voteAction);
    pmm.sendBook(player, forceOpen);
  }

  @Command(
      aliases = {"betchange", "changebet"},
      desc = "Change your bet",
      usage = "amount")
  public void betChange(
      MatchPlayer player, @Switch('o') boolean forceOpen, @Default("10") int change) {
    if (player.getParty() instanceof Competitor) {
      player.sendWarning(TextComponent.of("You cannot change your bet if you are playing!"));
      return;
    }
    Match match = player.getMatch();
    PredictionMatchModule pmm = match.needModule(PredictionMatchModule.class);
    if (!pmm.canPredict()) {
      player.sendWarning(TextComponent.of("Predictions have closed!"));
      return;
    }
    if (!pmm.changeBet(player.getId(), change)) {
      player.sendWarning(TextComponent.of("The minimum betting amount is 500!"));
      return;
    }
    pmm.sendBook(player, forceOpen);
  }
}
