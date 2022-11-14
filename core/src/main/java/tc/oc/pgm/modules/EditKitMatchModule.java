package tc.oc.pgm.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchModule;
import tc.oc.pgm.api.match.MatchScope;
import tc.oc.pgm.api.party.Competitor;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.events.ListenerScope;
import tc.oc.pgm.events.PlayerJoinPartyEvent;
import tc.oc.pgm.kits.ApplyItemKitEvent;
import tc.oc.pgm.kits.Kit;
import tc.oc.pgm.kits.KitNode;
import tc.oc.pgm.kits.Slot;
import tc.oc.pgm.spawns.ObserverToolFactory;
import tc.oc.pgm.spawns.Spawn;
import tc.oc.pgm.spawns.SpawnMatchModule;
import tc.oc.pgm.spawns.events.ObserverKitApplyEvent;

@ListenerScope(MatchScope.LOADED)
public class EditKitMatchModule implements MatchModule, Listener {
  private final Match match;
  private final HashMap<UUID, HashMap<Slot, ItemStack>> kitInventories = new HashMap<>();
  private final Set<UUID> playersEditing = new HashSet<>();

  public EditKitMatchModule(Match match) {
    this.match = match;
  }

  public boolean isEditing(final MatchPlayer player) {
    if (player == null) return false;
    return playersEditing.contains(player.getId());
  }

  @EventHandler
  public void onApplyItemEvent(final ApplyItemKitEvent e) {
    final Map<Slot, ItemStack> itemSlots = kitInventories.get(e.getPlayer().getId());
    if (itemSlots == null) return;
    e.getSlotItems().clear();
    for (Map.Entry<Slot, ItemStack> entry : itemSlots.entrySet()) {
      e.getSlotItems().put(entry.getKey(), entry.getValue().clone());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onInventoryClick(final InventoryClickEvent e) {
    if (e.getWhoClicked() instanceof Player) {
      final Player player = (Player) e.getWhoClicked();
      if (playersEditing.contains(player.getUniqueId())) {
        if (e.getSlotType() == InventoryType.SlotType.ARMOR) {
          e.setCancelled(true);
          return;
        }
        e.setCancelled(false);
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlayerQuit(final PlayerQuitEvent e) {
    quitPlayer(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlayerJoin(final PlayerJoinPartyEvent e) {
    quitPlayer(e.getPlayer(), true);
  }

  public boolean editKit(final MatchPlayer player, final Competitor party) {
    if (!player.isObserving() || player.getMatch() != match) return false;
    final SpawnMatchModule smm = match.getModule(SpawnMatchModule.class);
    if (smm == null) {
      player.sendWarning(TextComponent.of("Unable to find any kits!", TextColor.RED));
      return false;
    }
    final List<Spawn> results = smm.getSpawns(party);
    if (results.size() == 0) {
      player.sendWarning(TextComponent.of("Unable to find any kits!", TextColor.RED));
      return false;
    }
    final Spawn spawn = results.get(0);
    if (spawn.getKit().isPresent()) {
      final Kit n = spawn.getKit().get();
      if (n instanceof KitNode) {
        playersEditing.add(player.getId());
        replaceInventory(player, (KitNode) n);
        return true;
      }
    }
    player.sendWarning(TextComponent.of("Unable to find any kits!", TextColor.RED));
    return false;
  }

  private void replaceInventory(final MatchPlayer player, final KitNode kit) {
    player.getBukkit().setGameMode(GameMode.SURVIVAL);
    player.getBukkit().getInventory().clear();
    final List<ItemStack> displaced = new ArrayList<>();
    kit.apply(player, true, displaced);
  }

  public boolean saveKit(final MatchPlayer player) {
    if (!playersEditing.contains(player.getId())) return false;
    int i = -1;
    final HashMap<Slot, ItemStack> slots = new HashMap<>();
    for (ItemStack itemStack : player.getInventory().getContents()) {
      i++;
      if (itemStack == null) continue;
      slots.put(Slot.Player.forIndex(i), itemStack.clone());
    }
    kitInventories.put(player.getId(), slots);
    quitPlayer(player, false);
    return true;
  }

  public boolean quitPlayer(UUID uuid) {
    return playersEditing.remove(uuid);
  }

  public void quitPlayer(final MatchPlayer player, boolean join) {
    if (quitPlayer(player.getId())) {
      player.getBukkit().getInventory().setArmorContents(new ItemStack[4]);
      player.getBukkit().getInventory().clear();
      if (player.getBukkit().getOpenInventory() != null) {
        player.getBukkit().getOpenInventory().setCursor(null);
      }
      if (join) return;
      player.getBukkit().setGameMode(GameMode.CREATIVE);
      final SpawnMatchModule smm = match.getModule(SpawnMatchModule.class);
      if (smm == null) return;
      final ObserverToolFactory toolFactory = smm.getObserverToolFactory();
      player.getInventory().setItem(0, toolFactory.getTeleportTool(player.getBukkit()));
      if (toolFactory.canUseEditWand(player.getBukkit())) {
        player.getInventory().setItem(1, toolFactory.getEditWand(player.getBukkit()));
      }
      match.callEvent(new ObserverKitApplyEvent(player));
      smm.getDefaultSpawn().applyKit(player);
    }
  }
}
