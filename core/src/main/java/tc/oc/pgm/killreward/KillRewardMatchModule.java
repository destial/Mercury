package tc.oc.pgm.killreward;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.commons.lang3.mutable.MutableInt;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import tc.oc.pgm.api.event.PlayerItemTransferEvent;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchModule;
import tc.oc.pgm.api.match.MatchScope;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.ParticipantState;
import tc.oc.pgm.api.player.event.MatchPlayerDeathEvent;
import tc.oc.pgm.api.tracker.info.DamageInfo;
import tc.oc.pgm.events.ListenerScope;
import tc.oc.pgm.events.PlayerPartyChangeEvent;
import tc.oc.pgm.filters.query.DamageQuery;
import tc.oc.pgm.util.collection.DefaultMapAdapter;

@ListenerScope(MatchScope.RUNNING)
public class KillRewardMatchModule implements MatchModule, Listener {
  private final Match match;
  private final Map<UUID, MutableInt> killStreaks;
  private final ImmutableList<KillReward> killRewards;
  private final Multimap<UUID, KillReward> deadPlayerRewards;

  public KillRewardMatchModule(Match match, List<KillReward> killRewards) {
    this.match = match;
    this.killRewards = ImmutableList.copyOf(killRewards);
    this.killStreaks = new DefaultMapAdapter<>(key -> new MutableInt(), true);
    this.deadPlayerRewards = ArrayListMultimap.create();
  }

  public int getKillStreak(UUID uuid) {
    return killStreaks.get(uuid).intValue();
  }

  private Collection<KillReward> getRewards(
      @Nullable Event event, ParticipantState victim, DamageInfo damageInfo) {
    final DamageQuery query = DamageQuery.attackerDefault(event, victim, damageInfo);
    return Collections2.filter(
        killRewards, killReward -> killReward.filter.query(query).isAllowed());
  }

  private Collection<KillReward> getRewards(MatchPlayerDeathEvent event) {
    return getRewards(event, event.getVictim().getParticipantState(), event.getDamageInfo());
  }

  private void giveRewards(MatchPlayer killer, Collection<KillReward> rewards) {
    for (KillReward reward : rewards) {
      List<ItemStack> items = new ArrayList<>(reward.items);

      // Apply kit first so it can not override reward items
      reward.kit.apply(killer, false, items);

      for (ItemStack stack : items) {
        ItemStack clone = stack.clone();
        PlayerItemTransferEvent event =
            new PlayerItemTransferEvent(
                null,
                PlayerItemTransferEvent.Type.PLUGIN,
                killer.getBukkit(),
                null,
                null,
                killer.getBukkit().getInventory(),
                null,
                clone,
                null,
                clone.getAmount(),
                null);
        match.callEvent(event);
        if (!event.isCancelled() && event.getQuantity() > 0) {
          // BEWARE: addItem modifies its argument.. send in the clone!
          clone.setAmount(event.getQuantity());
          killer.getBukkit().getInventory().addItem(clone);
        }
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onDeath(MatchPlayerDeathEvent event) {
    final ParticipantState killer = event.getKiller();
    if (event.isChallengeKill() && killer != null) {
      killStreaks.get(killer.getId()).increment();
    }

    final MatchPlayer victim = event.getVictim();
    if (victim != null) {
      killStreaks.get(victim.getId()).setValue(0);
    }

    if (!event.isChallengeKill() || killer == null) return;
    MatchPlayer onlineKiller = killer.getPlayer().orElse(null);
    if (onlineKiller == null) return;

    Collection<KillReward> rewards = getRewards(event);

    if (onlineKiller.isDead()) {
      // If a player earns a KW while dead, give it to them when they respawn. Rationale: If they
      // click respawn
      // fast enough, they will get the reward anyway, and we can't prevent it in that case, so we
      // might as well
      // just give it to them always. Also, if the KW is in itemkeep, they should definitely get it
      // while dead,
      // and this is a relatively simple way to handle that case.
      deadPlayerRewards.putAll(onlineKiller.getId(), rewards);
    } else {
      giveRewards(onlineKiller, rewards);
    }
  }

  /**
   * This is called from {@link tc.oc.pgm.spawns.SpawnMatchModule} so that rewards are given after
   * kits
   */
  public void giveDeadPlayerRewards(MatchPlayer attacker) {
    giveRewards(attacker, deadPlayerRewards.removeAll(attacker.getId()));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPartyChange(PlayerPartyChangeEvent event) {
    killStreaks.remove(event.getPlayer().getId());
    deadPlayerRewards.removeAll(event.getPlayer().getId());
  }
}
