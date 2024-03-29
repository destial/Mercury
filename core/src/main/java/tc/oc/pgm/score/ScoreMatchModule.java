package tc.oc.pgm.score;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import java.time.Instant;
import java.util.*;
import java.util.Map.Entry;
import net.kyori.text.TextComponent;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.format.TextColor;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.event.CoarsePlayerMoveEvent;
import tc.oc.pgm.api.event.PlayerItemTransferEvent;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchModule;
import tc.oc.pgm.api.match.MatchScope;
import tc.oc.pgm.api.party.Competitor;
import tc.oc.pgm.api.party.event.CompetitorScoreChangeEvent;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.ParticipantState;
import tc.oc.pgm.api.player.event.MatchPlayerDeathEvent;
import tc.oc.pgm.events.ListenerScope;
import tc.oc.pgm.events.PlayerParticipationStartEvent;
import tc.oc.pgm.util.chat.Sound;
import tc.oc.pgm.util.collection.DefaultMapAdapter;
import tc.oc.pgm.util.material.matcher.SingleMaterialMatcher;
import tc.oc.pgm.util.named.NameStyle;

@ListenerScope(MatchScope.RUNNING)
public class ScoreMatchModule implements MatchModule, Listener {

  private final Match match;
  private final ScoreConfig config;
  private final Set<ScoreBox> scoreBoxes;
  private final Map<UUID, Double> contributions = new DefaultMapAdapter<>(new HashMap<>(), 0d);
  private final Map<Competitor, Double> scores = new DefaultMapAdapter<>(new HashMap<>(), 0d);
  private MercyRule mercyRule;
  private Integer scoreLimitOverride;

  public ScoreMatchModule(Match match, ScoreConfig config, Set<ScoreBox> scoreBoxes) {
    this.match = match;
    this.config = config;
    this.scoreBoxes = scoreBoxes;
    this.match.getCompetitors().forEach(competitor -> this.scores.put(competitor, 0.0));

    if (this.config.mercyLimit > 0) {
      this.mercyRule = new MercyRule(this, config.scoreLimit, config.mercyLimit);
    }
    scoreLimitOverride = null;
  }

  public Set<ScoreBox> getScoreBoxes() {
    return scoreBoxes;
  }

  @Override
  public void load() {
    match.addVictoryCondition(new ScoreVictoryCondition());
  }

  public boolean hasScoreLimit() {
    return this.config.scoreLimit > 0 || hasMercyRule() || scoreLimitOverride != null;
  }

  public boolean hasMercyRule() {
    return this.mercyRule != null;
  }

  public int getScoreLimit() {
    checkState(hasScoreLimit());

    if (hasMercyRule()) {
      return this.mercyRule.getScoreLimit();
    }

    return getScore();
  }

  public int getScore() {
    if (scoreLimitOverride != null) {
      return scoreLimitOverride;
    }

    return this.config.scoreLimit;
  }

  public Map<Competitor, Double> getScores() {
    return this.scores;
  }

  public void setScoreLimit(Integer limit) {
    scoreLimitOverride = limit;
  }

  public double getScore(Competitor competitor) {
    return this.scores.get(competitor);
  }

  /** Gets the score message for the match. */
  public String getScoreMessage() {
    List<String> scores = Lists.newArrayList();
    for (Entry<Competitor, Double> scorePair : this.scores.entrySet()) {
      scores.add(scorePair.getKey().getColor().toString() + ((int) (double) scorePair.getValue()));
    }
    return ChatColor.DARK_AQUA + "Score: " + Joiner.on(" ").join(scores);
  }

  /** Gets the status message for the match. */
  public String getStatusMessage() {
    StringBuilder message = new StringBuilder(this.getScoreMessage());
    if (this.getScore() > 0) {
      message.append("  ").append(ChatColor.GRAY).append("[").append(this.getScore()).append("]");
    }
    return message.toString();
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void incrementDeath(MatchPlayerDeathEvent event) {
    if (!event.getVictim().isParticipating()) return;

    // add +1 to killer's team if it was a kill, otherwise -1 to victim's team
    if (event.isChallengeKill()) {
      this.incrementScore(
          event.getKiller().getId(), event.getKiller().getParty(), this.config.killScore);
    } else {
      this.incrementScore(
          event.getVictim().getId(), event.getVictim().getCompetitor(), -this.config.deathScore);
    }
  }

  private double redeemItems(ScoreBox box, ItemStack stack) {
    if (stack == null) return 0;
    double points = 0;
    for (Entry<SingleMaterialMatcher, Double> entry : box.getRedeemables().entrySet()) {
      if (entry.getKey().matches(stack.getData())) {
        points += entry.getValue() * stack.getAmount();
        stack.setAmount(0);
      }
    }
    return points;
  }

  private double redeemItems(ScoreBox box, ItemStack[] stacks) {
    double total = 0;
    for (int i = 0; i < stacks.length; i++) {
      double points = redeemItems(box, stacks[i]);
      if (points != 0) stacks[i] = null;
      total += points;
    }
    return total;
  }

  private double redeemItems(ScoreBox box, PlayerInventory inventory) {
    ItemStack[] notArmor = inventory.getContents();
    ItemStack[] armor = inventory.getArmorContents();

    double points = redeemItems(box, notArmor) + redeemItems(box, armor);

    if (points != 0) {
      inventory.setContents(notArmor);
      inventory.setArmorContents(armor);
    }

    return points;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void playerEnterBox(CoarsePlayerMoveEvent event) {
    MatchPlayer player = this.match.getPlayer(event.getPlayer());
    if (player == null || !player.canInteract() || player.getBukkit().isDead()) return;

    ParticipantState playerState = player.getParticipantState();
    Vector from = event.getBlockFrom().toVector();
    Vector to = event.getBlockTo().toVector();

    for (ScoreBox box : this.scoreBoxes) {
      if (box.getRegion().enters(from, to) && box.canScore(playerState)) {
        if (box.isCoolingDown(playerState)) {
          match
              .getLogger()
              .warning(
                  playerState.getId()
                      + " tried to score multiple times in one second (from="
                      + from
                      + " to="
                      + to
                      + ")");
        } else {
          this.playerScore(box, player, box.getScore() + redeemItems(box, player.getInventory()));
        }
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void playerAcquireRedeemableInBox(final PlayerItemTransferEvent event) {
    if (!event.isAcquiring()) return;
    final MatchPlayer player = this.match.getPlayer(event.getPlayer());
    if (player == null || !player.canInteract() || player.getBukkit().isDead()) return;

    for (final ScoreBox box : this.scoreBoxes) {
      if (!box.getRedeemables().isEmpty()
          && box.getRegion().contains(player.getBukkit())
          && box.canScore(player.getParticipantState())) {
        match
            .getExecutor(MatchScope.RUNNING)
            .execute(
                () -> {
                  if (player.getBukkit().isOnline()) {
                    double points = redeemItems(box, player.getInventory());
                    ScoreMatchModule.this.playerScore(box, player, points);
                  }
                });
      }
    }
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void handleJoin(final PlayerParticipationStartEvent event) {
    double contribution = contributions.get(event.getPlayer().getId());
    if (contribution <= PGM.get().getConfiguration().getGriefScore()) {
      event.cancel(TranslatableComponent.of("join.err.teamGrief", TextColor.RED));
    }
  }

  private void playerScore(ScoreBox box, MatchPlayer player, double points) {
    checkState(player.isParticipating());

    if (points == 0) return;

    this.incrementScore(player.getId(), player.getCompetitor(), points);
    box.setLastScoreTime(player.getState(), Instant.now());

    int wholePoints = (int) points;
    if (wholePoints < 1) return;

    match.sendMessage(
        TranslatableComponent.of(
            "scorebox.scored",
            player.getName(NameStyle.COLOR),
            TranslatableComponent.of(
                wholePoints == 1 ? "misc.point" : "misc.points",
                TextComponent.of(Integer.toString(wholePoints), TextColor.DARK_AQUA)),
            player.getParty().getName()));
    player.playSound(new Sound("random.levelup"));
  }

  public void incrementScore(UUID player, Competitor competitor, double amount) {
    double contribution = contributions.get(player) + amount;
    contributions.put(player, contribution);
    incrementScore(competitor, amount);

    if (contribution <= PGM.get().getConfiguration().getGriefScore()) {
      MatchPlayer mp = match.getPlayer(player);
      if (mp == null) return;

      // wait until the next tick to do this so stat recording and other stuff works
      match
          .getExecutor(MatchScope.RUNNING)
          .execute(
              () -> {
                if (mp.getParty() instanceof Competitor) {
                  match.setParty(mp, match.getDefaultParty());
                  mp.sendWarning(TranslatableComponent.of("join.err.teamGrief", TextColor.RED));
                }
              });
    }
  }

  public void incrementScore(Competitor competitor, double amount) {
    double oldScore = this.scores.get(competitor);
    double newScore = oldScore + amount;

    if (getScore() > 0 && newScore > getScore()) {
      newScore = getScore();
    }

    CompetitorScoreChangeEvent event =
        new CompetitorScoreChangeEvent(competitor, oldScore, newScore);
    this.match.callEvent(event);

    this.scores.put(competitor, event.getNewScore());

    if (hasMercyRule()) {
      this.mercyRule.handleEvent(event);
    }

    this.match.calculateVictory();
  }
}
