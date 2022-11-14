package tc.oc.pgm.community.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.text.TextComponent;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.format.TextColor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tc.oc.pgm.PGMPlugin;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchModule;
import tc.oc.pgm.api.match.MatchScope;
import tc.oc.pgm.api.party.Competitor;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.event.ObserverInteractEvent;
import tc.oc.pgm.community.events.CPSStopEvent;
import tc.oc.pgm.community.events.PlayerReportEvent;
import tc.oc.pgm.events.ListenerScope;
import tc.oc.pgm.events.PlayerPartyChangeEvent;
import tc.oc.pgm.spawns.events.ObserverKitApplyEvent;
import tc.oc.pgm.util.named.NameStyle;
import tc.oc.pgm.util.text.TextTranslations;

@ListenerScope(MatchScope.LOADED)
public class CPSMatchModule implements MatchModule, Listener {

  private static final Material TOOL_MATERIAL = Material.FEATHER;
  private static final int TOOL_SLOT_NUM = 7;

  private static Map<UUID, CountedPlayer> countedPlayers;

  public CPSMatchModule(Match match) {
    countedPlayers = new HashMap<>();
  }

  public boolean isChecked(UUID id) {
    return countedPlayers.containsKey(id);
  }

  public CountedPlayer getPlayer(UUID id) {
    return countedPlayers.get(id);
  }

  private ItemStack getCPSTool(CommandSender viewer) {
    ItemStack stack = new ItemStack(TOOL_MATERIAL);
    ItemMeta meta = stack.getItemMeta();
    meta.setDisplayName(
        ChatColor.WHITE
            + ChatColor.BOLD.toString()
            + TextTranslations.translate("moderation.cps.itemName", viewer));
    meta.addItemFlags(ItemFlag.values());
    meta.setLore(
        Collections.singletonList(
            ChatColor.GRAY + TextTranslations.translate("moderation.cps.itemDescription", viewer)));
    stack.setItemMeta(meta);
    return stack;
  }

  @Override
  public void disable() {
    MatchModule.super.disable();
    for (CountedPlayer player : countedPlayers.values()) {
      player.stop();
    }
    countedPlayers.clear();
  }

  @EventHandler
  public void giveKit(final ObserverKitApplyEvent event) {
    Player player = event.getPlayer().getBukkit();
    player.getInventory().setItem(TOOL_SLOT_NUM, getCPSTool(player));
  }

  @EventHandler
  public void onPlayerJoinParty(PlayerPartyChangeEvent e) {
    if (e.getNewParty() instanceof Competitor) {
      if (isChecked(e.getPlayer().getId())) {
        countedPlayers.remove(e.getPlayer().getId()).stop();
      }
      CountedPlayer player = new CountedPlayer(e.getPlayer());
      countedPlayers.put(e.getPlayer().getId(), player);
      player.start(-1);

      for (CountedPlayer checked : countedPlayers.values()) {
        checked.removeChecker(e.getPlayer().getId());
      }
      return;
    }
    CountedPlayer player = countedPlayers.remove(e.getPlayer().getId());
    if (player == null) return;
    player.stop();
    countedPlayers.remove(player.player.getId());
  }

  @EventHandler
  public void onPlayerLeaveGame(PlayerQuitEvent e) {
    CountedPlayer player = countedPlayers.remove(e.getPlayer().getUniqueId());
    if (player == null) return;
    player.stop();
    countedPlayers.remove(player.player.getId());
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onObserverToolCPS(final ObserverInteractEvent event) {
    if (event.getPlayer().isDead()) return;
    if (event.getClickedPlayer() == null) return;
    if (event.getClickedItem() != null
        && event.getClickedItem().getType() == TOOL_MATERIAL
        && event.getClickedPlayer() != null) {
      event.setCancelled(true);
      CountedPlayer player = getPlayer(event.getClickedPlayer().getId());
      if (player == null) {
        player = new CountedPlayer(event.getClickedPlayer());
        countedPlayers.put(event.getClickedPlayer().getId(), player);
        player.start(-1);
      }
      player.addChecker(event.getPlayer());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onClick(PlayerInteractEvent e) {
    Player player = e.getPlayer();
    if (isChecked(player.getUniqueId())) {
      CountedPlayer checked = getPlayer(player.getUniqueId());
      switch (e.getAction()) {
        case LEFT_CLICK_AIR:
        case LEFT_CLICK_BLOCK:
          checked.addCPS(1);
          break;
        case RIGHT_CLICK_AIR:
        case RIGHT_CLICK_BLOCK:
          checked.addBuildCPS(1);
          break;
        default:
          break;
      }
    }
  }

  @EventHandler
  public void onStopCheck(CPSStopEvent e) {
    Match match = e.getMatchPlayer().getMatch();
    CountedPlayer checked = e.getCheckedPlayer();
    for (MatchPlayer checker : e.getCheckers()) {
      checker.sendMessage(
          TextComponent.builder()
              .append(
                  TranslatableComponent.of(
                      "moderation.cps.checked",
                      TextColor.DARK_GRAY,
                      e.getMatchPlayer().getName(NameStyle.COLOR),
                      TextComponent.of(checked.getMaxCps(), TextColor.YELLOW),
                      TextComponent.of(checked.getMaxBuildCps(), TextColor.YELLOW)))
              .build());
    }
    if (checked.getMaxCps() >= 15) {
      if (!checked.isReported()) {
        checked.report();
        match.callEvent(
            new PlayerReportEvent(
                checked.getPlayer(),
                checked.getPlayer(),
                "Left-Clicking " + checked.getMaxCps() + "cps"));
      }
    }
    if (checked.getMaxBuildCps() >= 15) {
      if (!checked.isReportedRight()) {
        checked.reportRight();
        match.callEvent(
            new PlayerReportEvent(
                checked.getPlayer(),
                checked.getPlayer(),
                "Right-Clicking " + checked.getMaxBuildCps() + "cps"));
      }
    }
  }

  public static class CountedPlayer {
    private final HashMap<MatchPlayer, Long> checkers;
    private final MatchPlayer player;
    private int cps;
    private int buildCPS;
    private int task;
    private int timer;
    private boolean reported;
    private boolean reportedRight;
    private int maxCps;
    private int maxBuildCps;

    private int cpsSecond;
    private int buildCpsSecond;

    public CountedPlayer(MatchPlayer player) {
      this.player = player;
      this.checkers = new HashMap<>();
      task = -1;
      resetCPS();
    }

    public void addChecker(MatchPlayer player) {
      if (checkers.containsKey(player)) return;
      checkers.put(player, System.currentTimeMillis() + 10000);
    }

    public void removeChecker(UUID uuid) {
      MatchPlayer checker =
          checkers.keySet().stream().filter(c -> c.getId().equals(uuid)).findFirst().orElse(null);
      if (checker == null) return;
      checkers.remove(checker);
    }

    public int getMaxCps() {
      return maxCps;
    }

    public int getMaxBuildCps() {
      return maxBuildCps;
    }

    public void start(final long time) {
      task =
          Bukkit.getScheduler()
              .scheduleAsyncRepeatingTask(
                  PGM.get(),
                  () -> {
                    if (!player.getBukkit().isOnline() || (time != -1 && timer >= time)) {
                      stop();
                      countedPlayers.remove(player.getId());
                      return;
                    }
                    List<MatchPlayer> toRemove = new ArrayList<>();
                    for (Map.Entry<MatchPlayer, Long> entry : checkers.entrySet()) {
                      MatchPlayer checker = entry.getKey();
                      long expire = entry.getValue();
                      if (checker.getBukkit().isOnline()) {
                        checker.showHotbar(
                            TextComponent.builder()
                                .append(
                                    TranslatableComponent.of(
                                        "moderation.cps.checked",
                                        TextColor.DARK_GRAY,
                                        player.getName(NameStyle.PLAIN),
                                        TextComponent.of(cpsSecond, TextColor.YELLOW),
                                        TextComponent.of(buildCpsSecond, TextColor.YELLOW)))
                                .build());
                        if (expire < System.currentTimeMillis()) {
                          toRemove.add(checker);
                        }
                      } else {
                        toRemove.add(checker);
                      }
                    }
                    for (MatchPlayer checker : toRemove) {
                      checkers.remove(checker);
                    }
                    if (cpsSecond > maxCps) {
                      maxCps = cpsSecond;
                    }
                    if (buildCpsSecond > maxBuildCps) {
                      maxBuildCps = buildCpsSecond;
                    }
                    timer++;
                    cpsSecond = 0;
                    buildCpsSecond = 0;
                    if (timer >= 10) {
                      player.getMatch().callEvent(new CPSStopEvent(this));
                      resetCPS();
                    }
                  },
                  0L,
                  (long) PGMPlugin.TPS.getTPS());
    }

    public void report() {
      if (!reported) {
        reported = true;
      }
    }

    public void reportRight() {
      if (!reportedRight) {
        reportedRight = true;
      }
    }

    public boolean isReportedRight() {
      return reportedRight;
    }

    public boolean isReported() {
      return reported;
    }

    public void stop() {
      if (task == -1) return;
      Bukkit.getScheduler().cancelTask(task);
      task = -1;
      player.getMatch().callEvent(new CPSStopEvent(this));
      checkers.clear();
      resetCPS();
    }

    public void addCPS(int count) {
      cps += count;
      cpsSecond += count;
    }

    public void addBuildCPS(int count) {
      buildCPS += count;
      buildCpsSecond += count;
    }

    public void resetCPS() {
      cps = 0;
      buildCPS = 0;
      timer = 0;
      cpsSecond = 0;
      buildCpsSecond = 0;
      maxBuildCps = 0;
      maxCps = 0;
      reported = false;
      reportedRight = false;
    }

    public int getAverageCPS() {
      if (cps == 0) return 0;
      if (timer == 0) return cps;
      return cps / timer;
    }

    public int getCPS() {
      return cpsSecond;
    }

    public int getBuildCPS() {
      return buildCpsSecond;
    }

    public int getAverageBuildCPS() {
      if (buildCPS == 0) return 0;
      if (timer == 0) return buildCPS;
      return buildCPS / timer;
    }

    public MatchPlayer getPlayer() {
      return player;
    }

    public Set<MatchPlayer> getCheckers() {
      return checkers.keySet();
    }
  }
}
