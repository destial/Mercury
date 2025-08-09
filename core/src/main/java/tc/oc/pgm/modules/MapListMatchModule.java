package tc.oc.pgm.modules;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.format.TextColor;
import net.kyori.text.format.TextDecoration;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.map.MapInfo;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchModule;
import tc.oc.pgm.api.match.MatchScope;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.events.ListenerScope;
import tc.oc.pgm.menu.InventoryMenu;
import tc.oc.pgm.menu.InventoryMenuItem;
import tc.oc.pgm.restart.RestartManager;
import tc.oc.pgm.rotation.MapPool;
import tc.oc.pgm.rotation.MapPoolManager;
import tc.oc.pgm.spawns.events.ObserverKitApplyEvent;
import tc.oc.pgm.util.named.MapNameStyle;
import tc.oc.pgm.util.text.TextTranslations;

@ListenerScope(MatchScope.LOADED)
public class MapListMatchModule implements MatchModule, Listener {
  private final Match match;
  private static final List<MapListMenu> menus = Lists.newArrayList();
  private static InventoryMenu shopMenu;
  private static boolean canRegister = true;

  public MapListMatchModule(Match match) {
    this.match = match;
    register();
  }

  private static void register() {
    if (!canRegister) return;
    canRegister = false;
    menus.clear();
    MapPool mapPool = ((MapPoolManager) PGM.get().getMapOrder()).getActiveMapPool();
    List<MapInfo> maps = mapPool.getMaps();
    for (int i = 0, page = 0; i < maps.size(); i += 27) {
      menus.add(new MapListMenu(++page, maps));
      if (i + 27 > maps.size()) {
        menus.add(new MapListMenu(++page, maps));
        break;
      }
    }
    shopMenu =
        new InventoryMenu("shop.title", 1) {
          @Override
          public ItemStack[] createWindowContents(MatchPlayer player) {
            ItemStack[] items = new ItemStack[9];
            items[2] = new ItemStack(Material.getMaterial(351), 1, DyeColor.SILVER.getData());
            ItemMeta meta = items[2].getItemMeta();
            meta.setDisplayName("" + ChatColor.WHITE + ChatColor.BOLD + "Buy Suffixes");
            items[2].setItemMeta(meta);

            items[4] = new ItemStack(Material.BOOK);
            meta = items[4].getItemMeta();
            meta.setDisplayName("" + ChatColor.GREEN + ChatColor.BOLD + "Buy Map Cycle");
            items[4].setItemMeta(meta);

            items[6] = new ItemStack(Material.NAME_TAG);
            meta = items[6].getItemMeta();
            meta.setDisplayName("" + ChatColor.LIGHT_PURPLE + ChatColor.BOLD + "My Suffixes");
            items[6].setItemMeta(meta);
            return items;
          }
        };
  }

  @EventHandler
  public void onObserverKitApply(ObserverKitApplyEvent event) {
    refreshKit(event.getPlayer());
  }

  @EventHandler
  public void onToolClick(final PlayerInteractEvent event) {
    if (isRightClick(event.getAction())) {
      ItemStack item = event.getPlayer().getItemInHand();
      if (item.getType().equals(Material.BOOK)) {
        MatchPlayer player = match.getPlayer(event.getPlayer());
        if (player == null) return;
        if (player.isObserving()) {
          shopMenu.display(player);
        }
      }
    }
  }

  @EventHandler
  public void onInventoryClose(final InventoryCloseEvent event) {
    MatchPlayer player = match.getPlayer((Player) event.getPlayer());
    shopMenu.remove(player);
    for (MapListMenu menu : menus) {
      menu.remove(player);
    }
  }

  @EventHandler(priority = EventPriority.LOW)
  public void onInventoryClick(final InventoryClickEvent event) {
    if (event.getCurrentItem() == null
        || event.getCurrentItem().getItemMeta() == null
        || event.getCurrentItem().getItemMeta().getDisplayName() == null) return;
    if (event.getWhoClicked() instanceof Player) {
      MatchPlayer player = match.getPlayer(event.getWhoClicked());
      if (player == null) return;
      ItemStack clicked = event.getCurrentItem();
      if (shopMenu.isViewing(player)) {
        if (clicked.getType() == Material.getMaterial(351)) {
          match.needModule(SuffixMatchModule.class).display(player);
        } else if (clicked.getType() == Material.BOOK) {
          menus.get(0).display(player);
        } else if (clicked.getType() == Material.NAME_TAG) {
          match.needModule(SuffixMatchModule.class).displaySelf(player);
        }
        return;
      }
      for (MapListMenu menu : menus) {
        if (menu.isViewing(player)) {
          menu.getMaps()
              .forEach(
                  tool -> {
                    if (tool.createItem(player).equals(clicked)) {
                      tool.onInventoryClick(menu, player, event.getClick());
                    }
                  });
          break;
        }
      }
    }
  }

  private static final int TOOL_NUM_SLOT = 6;

  private void refreshKit(final MatchPlayer player) {
    player.getInventory().setItem(TOOL_NUM_SLOT, createToolItem(player));
  }

  private boolean isRightClick(Action action) {
    return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
  }

  private ItemStack createToolItem(MatchPlayer player) {
    ItemStack tool = new ItemStack(Material.BOOK);
    ItemMeta meta = tool.getItemMeta();
    Component displayName =
        TranslatableComponent.of("shop.title", TextColor.AQUA, TextDecoration.BOLD);
    Component lore = TranslatableComponent.of("shop.lore", TextColor.GRAY);
    meta.setDisplayName(TextTranslations.translateLegacy(displayName, player.getBukkit()));
    meta.setLore(Lists.newArrayList(TextTranslations.translateLegacy(lore, player.getBukkit())));
    meta.addItemFlags(ItemFlag.values());
    tool.setItemMeta(meta);
    return tool;
  }

  public static class MapListMenu extends InventoryMenu {
    private final List<InventoryMenuItem> maps = new ArrayList<>();

    public MapListMenu(int page, List<MapInfo> maps) {
      super("maplist.title", 4);
      int starting = (page - 1) * 27;
      int pages =
          (maps.size() / 27f) != (int) (maps.size() / 27) ? maps.size() / 27 + 1 : maps.size() / 27;
      boolean more = true;
      for (int i = starting; i < starting + 27; i++) {
        if (i >= maps.size()) {
          more = false;
          break;
        }
        MapInfo map = maps.get(i);
        this.maps.add(new MapMenuItem(map));
      }
      if (starting != 0) {
        this.maps.add(new PageMenuItem(page, pages, false));
      }
      if (more) {
        this.maps.add(new PageMenuItem(page, pages, true));
      }
      this.maps.add(new CoinsMenuItem());
    }

    public List<InventoryMenuItem> getMaps() {
      return maps;
    }

    @Override
    public ItemStack[] createWindowContents(MatchPlayer player) {
      ItemStack[] items = new ItemStack[36];
      int i = 0;
      for (InventoryMenuItem map : maps) {
        if (map instanceof CoinsMenuItem) {
          items[31] = map.createItem(player);
        } else if (map instanceof PageMenuItem) {
          PageMenuItem page = (PageMenuItem) map;
          items[page.next ? 35 : 27] = page.createItem(player);
        } else {
          items[i] = map.createItem(player);
          i++;
        }
      }
      return items;
    }
  }

  public static class MapMenuItem implements InventoryMenuItem {
    private final MapInfo map;

    public MapMenuItem(MapInfo map) {
      this.map = map;
    }

    @Override
    public Component getName() {
      return map.getStyledName(MapNameStyle.PLAIN);
    }

    @Override
    public ChatColor getColor() {
      return ChatColor.AQUA;
    }

    @Override
    public List<String> getLore(MatchPlayer player) {
      Component component =
          TextComponent.of(
              map.getTags().stream().map(t -> "#" + t.getId()).collect(Collectors.joining(" ")),
              TextColor.YELLOW);
      return Lists.newArrayList(TextTranslations.translateLegacy(component, player.getBukkit()));
    }

    @Override
    public Material getMaterial(MatchPlayer player) {
      return Material.PAPER;
    }

    @Override
    public void onInventoryClick(InventoryMenu menu, MatchPlayer player, ClickType clickType) {
      if (Bukkit.getPluginManager().getPlugin("Events") != null) {
        player.sendWarning(TextComponent.of("Coins are currently disabled!"));
        return;
      }
      if (player.getCoins().getCoins() < PGM.get().getShop().getBuyMapCost()) {
        player.sendWarning(
            TextComponent.of(
                "You do not have enough coins! 10,000 coins is needed to buy a map cycle!",
                TextColor.RED));
        return;
      }
      if (PGM.get().getMapOrder().getNextMap() != null) {
        player.sendWarning(TextComponent.of("There is already a map set after this!"));
        return;
      }
      if (RestartManager.isQueued()) {
        player.sendWarning(TextComponent.of("Server is restarting!"));
        return;
      }
      if (map == player.getMatch().getMap()) {
        player.sendWarning(TextComponent.of("You cannot set the same map as the current!"));
        return;
      }

      player.getCoins().removeCoins(PGM.get().getShop().getBuyMapCost());
      PGM.get().getMapOrder().setNextMap(map);
      player.sendMessage(
          TextComponent.of("You have set the next map to be: " + map.getName(), TextColor.GREEN));
      player
          .getMatch()
          .sendMessage(
              TextComponent.of(
                  player.getNameLegacy()
                      + " has bought a map cycle to "
                      + map.getName()
                      + " for "
                      + PGM.get().getShop().getBuyMapCost()
                      + " coins!",
                  TextColor.AQUA));

      player.getBukkit().closeInventory();
      menu.remove(player);
    }
  }

  public static class PageMenuItem implements InventoryMenuItem {
    private final boolean next;
    private final int pages;
    private final int page;

    public PageMenuItem(int page, int pages, boolean next) {
      this.page = page;
      this.pages = pages;
      this.next = next;
    }

    @Override
    public Component getName() {
      return (next
          ? TextComponent.of("Next Page", TextColor.GREEN)
          : TextComponent.of("Previous Page", TextColor.RED));
    }

    @Override
    public ChatColor getColor() {
      return (next ? ChatColor.GREEN : ChatColor.RED);
    }

    @Override
    public List<String> getLore(MatchPlayer player) {
      return Lists.newArrayList(ChatColor.WHITE + "Page " + page + "/" + pages);
    }

    @Override
    public Material getMaterial(MatchPlayer player) {
      return Material.STAINED_GLASS;
    }

    @Override
    public void onInventoryClick(InventoryMenu menu, MatchPlayer player, ClickType clickType) {
      if (menu instanceof MapListMenu) {
        MapListMenu mapListMenu = (MapListMenu) menu;
        if (clickType.isLeftClick()) {
          int index = menus.indexOf(mapListMenu);
          menu.remove(player);
          if (next) {
            mapListMenu = menus.get(index + 1);
          } else {
            mapListMenu = menus.get(index - 1);
          }
          mapListMenu.display(player);
        }
      }
    }

    @Override
    public ItemStack createItem(MatchPlayer player) {
      ItemStack stack =
          new ItemStack(
              getMaterial(player), 1, (next ? DyeColor.LIME.getData() : DyeColor.RED.getData()));
      ItemMeta meta = stack.getItemMeta();
      meta.setDisplayName(
          getColor()
              + ChatColor.BOLD.toString()
              + TextTranslations.translateLegacy(getName(), player.getBukkit()));
      meta.setLore(getLore(player));
      meta.addItemFlags(ItemFlag.values());
      stack.setItemMeta(meta);
      return stack;
    }
  }

  public static class CoinsMenuItem implements InventoryMenuItem {
    public CoinsMenuItem() {}

    @Override
    public Component getName() {
      return TextComponent.of("Coins: ");
    }

    @Override
    public ChatColor getColor() {
      return ChatColor.YELLOW;
    }

    @Override
    public List<String> getLore(MatchPlayer player) {
      return Lists.newArrayList();
    }

    @Override
    public Material getMaterial(MatchPlayer player) {
      return Material.DOUBLE_PLANT;
    }

    @Override
    public void onInventoryClick(InventoryMenu menu, MatchPlayer player, ClickType clickType) {}

    @Override
    public ItemStack createItem(MatchPlayer player) {
      ItemStack stack = new ItemStack(getMaterial(player), 1);
      ItemMeta meta = stack.getItemMeta();
      meta.setDisplayName(
          getColor()
              + ChatColor.BOLD.toString()
              + TextTranslations.translateLegacy(
                  getName().append(TextComponent.of(player.getCoins().getCoins())),
                  player.getBukkit()));
      meta.setLore(getLore(player));
      meta.addItemFlags(ItemFlag.values());
      stack.setItemMeta(meta);
      return stack;
    }
  }
}
