package tc.oc.pgm.observers;

import com.google.common.collect.Lists;
import java.util.List;
import net.kyori.text.Component;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.format.TextColor;
import net.kyori.text.format.TextDecoration;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.SkullType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchModule;
import tc.oc.pgm.api.match.MatchScope;
import tc.oc.pgm.api.match.factory.MatchModuleFactory;
import tc.oc.pgm.api.module.exception.ModuleLoadException;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.events.ListenerScope;
import tc.oc.pgm.events.PlayerJoinMatchEvent;
import tc.oc.pgm.menu.InventoryMenu;
import tc.oc.pgm.menu.InventoryMenuItem;
import tc.oc.pgm.observers.tools.*;
import tc.oc.pgm.spawns.events.ObserverKitApplyEvent;
import tc.oc.pgm.util.named.NameStyle;
import tc.oc.pgm.util.text.TextTranslations;

@ListenerScope(MatchScope.LOADED)
public class ObserverToolsMatchModule implements MatchModule, Listener {

  public static class Factory implements MatchModuleFactory<ObserverToolsMatchModule> {
    @Override
    public ObserverToolsMatchModule createMatchModule(Match match) throws ModuleLoadException {
      return new ObserverToolsMatchModule(match);
    }
  }

  // Slot where tool item is placed
  public static final int TOOL_BUTTON_SLOT = 8;

  // Material of tool item item
  public static final Material TOOL_MATERIAL = Material.DIAMOND;

  private final Match match;
  private final ObserverToolMenu menu;
  private final TeleportToolMenu teleportMenu;

  public ObserverToolsMatchModule(Match match) {
    this.match = match;
    this.menu = new ObserverToolMenu();
    teleportMenu = new TeleportToolMenu(match);
  }

  @EventHandler
  public void onObserverKitApply(ObserverKitApplyEvent event) {
    refreshKit(event.getPlayer());
  }

  @EventHandler
  public void onLeave(PlayerQuitEvent e) {
    for (MatchPlayer player : match.getPlayers()) {
      if (teleportMenu.isViewing(player)) {
        teleportMenu.refreshWindow(player);
      }
    }
  }

  @EventHandler
  public void onJoin(PlayerJoinMatchEvent e) {
    for (MatchPlayer player : match.getPlayers()) {
      if (teleportMenu.isViewing(player)) {
        teleportMenu.refreshWindow(player);
      }
    }
  }

  @EventHandler
  public void onDrop(PlayerDropItemEvent e) {
    MatchPlayer player = match.getPlayer(e.getPlayer());
    if (player != null && player.isObserving()) e.setCancelled(true);
  }

  @EventHandler
  public void onToolClick(PlayerInteractEvent event) {
    if (isRightClick(event.getAction())) {
      ItemStack item = event.getPlayer().getItemInHand();

      if (item.getType().equals(TOOL_MATERIAL)) {
        MatchPlayer player = match.getPlayer(event.getPlayer());
        openMenu(player);
      } else if (item.getType().equals(Material.COMPASS)) {
        MatchPlayer player = match.getPlayer(event.getPlayer());
        openTeleportMenu(player);
      }
    }
  }

  @EventHandler(priority = EventPriority.LOW)
  public void onInventoryClick(final InventoryClickEvent event) {
    if (event.getCurrentItem() == null
        || event.getCurrentItem().getItemMeta() == null
        || event.getCurrentItem().getItemMeta().getDisplayName() == null) return;

    if (event.getWhoClicked() instanceof Player) {
      MatchPlayer player = match.getPlayer(event.getWhoClicked());
      if (!canUse(player)) return;
      if (menu.isViewing(player)) {
        ItemStack clicked = event.getCurrentItem();
        menu.getTools()
            .forEach(
                tool -> {
                  if (clicked.getType().equals(tool.getMaterial(player))) {
                    tool.onInventoryClick(menu, player, event.getClick());
                  }
                });
      } else if (teleportMenu.isViewing(player)) {
        ItemStack clicked = event.getCurrentItem();
        ItemMeta meta = clicked.getItemMeta();
        if (meta instanceof SkullMeta) {
          SkullMeta head = (SkullMeta) meta;
          String name = head.getOwner();
          if (name == null) return;
          Player teleport = Bukkit.getPlayer(name);
          if (teleport == null) return;
          player.getBukkit().teleport(teleport);
        }
      }
    }
  }

  @EventHandler
  public void onInventoryClose(final InventoryCloseEvent event) {
    // Remove viewing of menu upon inventory close
    MatchPlayer player = match.getPlayer((Player) event.getPlayer());
    menu.remove(player);
    teleportMenu.remove(player);
  }

  public void openMenu(MatchPlayer player) {
    if (canUse(player)) {
      menu.display(player);
    }
  }

  public void openTeleportMenu(MatchPlayer player) {
    if (canUse(player)) {
      teleportMenu.display(player);
    }
  }

  private boolean canUse(MatchPlayer player) {
    return player != null && player.isObserving();
  }

  private void refreshKit(MatchPlayer player) {
    if (canUse(player)) {
      player.getInventory().setItem(TOOL_BUTTON_SLOT, createToolItem(player));
    }
  }

  private boolean isRightClick(Action action) {
    return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
  }

  private ItemStack createToolItem(MatchPlayer player) {
    ItemStack tool = new ItemStack(TOOL_MATERIAL);
    ItemMeta meta = tool.getItemMeta();
    Component displayName =
        TranslatableComponent.of("setting.displayName", TextColor.AQUA, TextDecoration.BOLD);
    Component lore = TranslatableComponent.of("setting.lore", TextColor.GRAY);
    meta.setDisplayName(TextTranslations.translateLegacy(displayName, player.getBukkit()));
    meta.setLore(Lists.newArrayList(TextTranslations.translateLegacy(lore, player.getBukkit())));
    meta.addItemFlags(ItemFlag.values());
    tool.setItemMeta(meta);
    return tool;
  }

  public static class ObserverToolMenu extends InventoryMenu {

    public static final String INVENTORY_TITLE = "setting.title";
    public static final int INVENTORY_ROWS = 1;

    private List<InventoryMenuItem> tools;

    public ObserverToolMenu() {
      super(INVENTORY_TITLE, INVENTORY_ROWS);
      registerTools();
    }

    public List<InventoryMenuItem> getTools() {
      return tools;
    }

    // Register each of the observer tools
    // TODO?: Add config options to enable/disable each tool
    private void registerTools() {
      tools = Lists.newArrayList();
      tools.add(new AutoJoinTool());
      tools.add(new EloTool());
      tools.add(new FlySpeedTool());
      tools.add(new NightVisionTool());
      tools.add(new VisibilityTool());
      tools.add(new GamemodeTool());
      tools.add(new BloodTool());
    }

    @Override
    public String getTranslatedTitle(MatchPlayer player) {
      return ChatColor.AQUA + super.getTranslatedTitle(player);
    }

    @Override
    public ItemStack[] createWindowContents(MatchPlayer player) {
      List<ItemStack> items = Lists.newArrayList();

      items.add(null);
      for (InventoryMenuItem tool : tools) {
        items.add(tool.createItem(player));
      }
      return items.toArray(new ItemStack[0]);
    }
  }

  public static class TeleportToolMenu extends InventoryMenu {
    private final Match match;

    public TeleportToolMenu(Match match) {
      super("misc.teleportTool", 5);
      this.match = match;
    }

    @Override
    public ItemStack[] createWindowContents(MatchPlayer player) {
      List<ItemStack> items = Lists.newArrayList();
      for (MatchPlayer other : match.getPlayers()) {
        if (other == player || PGM.get().getVanishManager().isVanished(other.getId())) continue;
        ItemStack head = new ItemStack(Material.SKULL_ITEM, 1, (short) SkullType.PLAYER.ordinal());
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwner(other.getBukkit().getName(), other.getId(), other.getBukkit().getSkin());
        meta.setDisplayName(
            TextTranslations.translateLegacy(other.getName(NameStyle.COLOR), player.getBukkit()));
        head.setItemMeta(meta);
        items.add(head);
      }
      return items.toArray(new ItemStack[0]);
    }
  }
}
