package tc.oc.pgm.modules;

import com.google.common.collect.Lists;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import net.kyori.text.format.TextDecoration;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.json.JSONArray;
import tc.oc.pgm.api.Config;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.Permissions;
import tc.oc.pgm.api.coins.Coins;
import tc.oc.pgm.api.event.NameDecorationChangeEvent;
import tc.oc.pgm.api.event.ReloadEvent;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchModule;
import tc.oc.pgm.api.match.MatchScope;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.PlayerData;
import tc.oc.pgm.api.player.event.MatchPlayerAddEvent;
import tc.oc.pgm.events.ListenerScope;
import tc.oc.pgm.menu.InventoryMenu;
import tc.oc.pgm.menu.InventoryMenuItem;
import tc.oc.pgm.util.named.NameStyle;
import tc.oc.pgm.util.text.TextTranslations;

@ListenerScope(MatchScope.LOADED)
public class SuffixMatchModule implements MatchModule, Listener {
  private static Match match;
  public static final List<Suffix> ALL_SUFFIXES = new ArrayList<>();
  private static final List<SuffixesMenu> MENUS = new ArrayList<>();
  private static final HashMap<UUID, List<SuffixesMenu>> OWNED_MENUS = new HashMap<>();
  private static final Map<UUID, PermissionAttachment> attachmentMap = new HashMap<>();
  private static int COST = 5000;
  private static long COUNT;
  private static boolean canRegister = true;

  public SuffixMatchModule(Match match) {
    SuffixMatchModule.match = match;
    registerGroups();
  }

  private static void registerGroups() {
    if (!canRegister) return;
    canRegister = false;
    ALL_SUFFIXES.clear();
    File suffixFile = new File(PGM.get().getDataFolder(), "suffixes.yml");
    if (!suffixFile.exists()) PGM.get().saveResource("suffixes.yml", false);
    FileConfiguration suffixConfig = YamlConfiguration.loadConfiguration(suffixFile);
    COST = Math.abs(suffixConfig.getInt("cost", 5000));
    for (String name : suffixConfig.getConfigurationSection("suffixes").getKeys(false)) {
      for (TextColor color : TextColor.values()) {
        Suffix s = new Suffix(name, suffixConfig.getString("suffixes." + name), color);
        ALL_SUFFIXES.add(s);
        PGM.get().getConfiguration().getGroups().add(s);
      }
    }
    if (suffixConfig.contains("premium")) {
      for (String name : suffixConfig.getConfigurationSection("premium").getKeys(false)) {
        for (TextColor color : TextColor.values()) {
          Suffix s = new Suffix(name, suffixConfig.getString("premium." + name), color);
          s.premium = true;
          ALL_SUFFIXES.add(s);
          PGM.get().getConfiguration().getGroups().add(s);
        }
      }
    }
    for (MatchPlayer player : match.getPlayers()) {
      if (OWNED_MENUS.containsKey(player.getId())) {
        for (SuffixesMenu omenu : OWNED_MENUS.get(player.getId())) {
          omenu.remove(player);
        }
        OWNED_MENUS.remove(player.getId());
        continue;
      }
      for (SuffixesMenu menu : MENUS) {
        if (menu.isViewing(player)) {
          menu.remove(player);
          player.getBukkit().closeInventory();
        }
      }
    }
    OWNED_MENUS.clear();
    MENUS.clear();
    COUNT = ALL_SUFFIXES.stream().filter(suffix -> !suffix.premium).count();
    for (int i = 0, page = 0; i < COUNT; i += 27) {
      MENUS.add(new SuffixesMenu(++page));
      if (i + 27 > COUNT) {
        MENUS.add(new SuffixesMenu(++page));
        break;
      }
    }
    Collection<Map.Entry<UUID, PermissionAttachment>> list = attachmentMap.entrySet();
    for (Map.Entry<UUID, PermissionAttachment> entry : list) {
      MatchPlayer player = match.getPlayer(entry.getKey());
      if (player == null) continue;
      entry.getValue().remove();
      PermissionAttachment attachment =
          player
              .getBukkit()
              .addAttachment(
                  PGM.get(),
                  entry.getValue().getPermissions().keySet().stream().findFirst().get(),
                  true);
      entry.setValue(attachment);
    }
  }

  public void displaySelf(MatchPlayer player) {
    OWNED_MENUS.remove(player.getId());
    List<SuffixesMenu> menus = new ArrayList<>();
    PlayerData data = player.getPlayerData();
    int size = 1;
    if (data.getData().has("suffixes")) {
      size = data.getData().getJSONArray("suffixes").length();
    }
    for (int i = 0, page = 0; i < size; i += 27) {
      menus.add(new SuffixesMenu(++page, player));
      if (i + 27 > size) {
        menus.add(new SuffixesMenu(++page, player));
        break;
      }
    }
    menus.get(0).display(player);
    OWNED_MENUS.put(player.getId(), menus);
  }

  public void display(MatchPlayer player) {
    OWNED_MENUS.remove(player.getId());
    MENUS.get(0).display(player);
  }

  @EventHandler
  public void onInventoryClose(final InventoryCloseEvent event) {
    MatchPlayer player = match.getPlayer((Player) event.getPlayer());
    if (player == null) return;
    if (OWNED_MENUS.containsKey(player.getId())) {
      for (SuffixesMenu menu : OWNED_MENUS.get(player.getId())) {
        if (menu.isViewing(player)) menu.remove(player);
      }
    }
    for (SuffixesMenu menu : MENUS) {
      if (menu.isViewing(player)) menu.remove(player);
    }
  }

  @EventHandler
  public void onReload(final ReloadEvent e) {
    canRegister = true;
    registerGroups();
  }

  @EventHandler
  public void onJoin(final MatchPlayerAddEvent e) {
    PlayerData data = e.getPlayer().getPlayerData();
    if (attachmentMap.containsKey(e.getPlayer().getId())) return;
    if (data.getData().has("current_suffix")) {
      PermissionAttachment attachment =
          e.getPlayer()
              .getBukkit()
              .addAttachment(PGM.get(), data.getData().getString("current_suffix"), true);
      attachmentMap.put(e.getPlayer().getId(), attachment);
      match.callEvent(new NameDecorationChangeEvent(e.getPlayer().getId()));
    } else {
      match
          .getExecutor(MatchScope.LOADED)
          .schedule(
              () -> {
                PlayerData pdata = e.getPlayer().getPlayerData();
                if (attachmentMap.containsKey(e.getPlayer().getId())) return;
                if (pdata.getData().has("current_suffix")) {
                  PermissionAttachment attachment =
                      e.getPlayer()
                          .getBukkit()
                          .addAttachment(
                              PGM.get(), pdata.getData().getString("current_suffix"), true);
                  attachmentMap.put(e.getPlayer().getId(), attachment);
                  match.callEvent(new NameDecorationChangeEvent(e.getPlayer().getId()));
                }
              },
              1L,
              TimeUnit.SECONDS);
    }
  }

  @EventHandler
  public void onLeave(PlayerQuitEvent e) {
    if (attachmentMap.containsKey(e.getPlayer().getUniqueId()))
      attachmentMap.remove(e.getPlayer().getUniqueId()).remove();
    if (OWNED_MENUS.containsKey(e.getPlayer().getUniqueId()))
      OWNED_MENUS.remove(e.getPlayer().getUniqueId()).clear();
  }

  @EventHandler(priority = EventPriority.LOW)
  public void onInventoryClick(final InventoryClickEvent event) {
    if (event.getCurrentItem() == null) return;
    ItemMeta meta = event.getCurrentItem().getItemMeta();
    if (meta == null || meta.getDisplayName() == null) return;
    if (event.getWhoClicked() instanceof Player) {
      MatchPlayer player = match.getPlayer(event.getWhoClicked());
      if (player == null) return;
      List<SuffixesMenu> menuList = OWNED_MENUS.getOrDefault(player.getId(), MENUS);
      for (SuffixesMenu menu : menuList) {
        if (menu.isViewing(player)) {
          ItemStack clicked = event.getCurrentItem();
          menu.getMenuItems()
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

  public static class SuffixesMenu extends InventoryMenu {
    private final List<InventoryMenuItem> menuItems = new ArrayList<>();
    private final MatchPlayer player;

    public MatchPlayer getPlayer() {
      return player;
    }

    public SuffixesMenu(int page, MatchPlayer player) {
      super("suffixes.title", 4);
      this.player = player;
      PlayerData data = player.getPlayerData();
      JSONArray array;
      if (data.getData().has("suffixes")) array = data.getData().getJSONArray("suffixes");
      else array = new JSONArray();
      List<Suffix> suffixes =
          array.length() > 0
              ? ALL_SUFFIXES.stream()
                  .filter(
                      suffix -> {
                        for (Object o : array) {
                          String name = (String) o;
                          if (suffix.getId().equals(name)) {
                            return true;
                          }
                        }
                        return false;
                      })
                  .collect(Collectors.toList())
              : new ArrayList<>();
      int pages =
          (suffixes.size() / 27f) != (int) (suffixes.size() / 27)
              ? suffixes.size() / 27 + 1
              : suffixes.size() / 27;
      int starting = (page - 1) * 27;
      boolean more = true;
      for (int i = starting; i < starting + 27; i++) {
        if (i >= suffixes.size()) {
          more = false;
          break;
        }
        Suffix suffix = suffixes.get(i);
        menuItems.add(new SuffixItem(suffix, true));
      }
      if (starting != 0) {
        menuItems.add(new PageSuffixItem(page, pages, false));
      }
      if (more) {
        menuItems.add(new PageSuffixItem(page, pages, true));
      }
      menuItems.add(new ClearSuffixItem());
    }

    public SuffixesMenu(int page) {
      super("suffixes.title", 4);
      this.player = null;
      int starting = (page - 1) * 27;
      int pages = (int) ((COUNT / 27f) != (int) (COUNT / 27) ? COUNT / 27 + 1 : COUNT / 27);
      boolean more = true;
      int i = starting;
      for (int j = starting; j < starting + 27; j++) {
        if (i >= COUNT) {
          more = false;
          break;
        }
        Suffix suffix = ALL_SUFFIXES.get(i++);
        if (suffix.premium) {
          continue;
        }
        menuItems.add(new SuffixItem(suffix, false));
      }
      if (starting != 0) {
        menuItems.add(new PageSuffixItem(page, pages, false));
      }
      if (more) {
        menuItems.add(new PageSuffixItem(page, pages, true));
      }
      menuItems.add(new MapListMatchModule.CoinsMenuItem());
    }

    public List<InventoryMenuItem> getMenuItems() {
      return menuItems;
    }

    @Override
    public ItemStack[] createWindowContents(MatchPlayer player) {
      ItemStack[] items = new ItemStack[36];
      int i = 0;
      for (InventoryMenuItem suffix : menuItems) {
        if (suffix instanceof MapListMatchModule.CoinsMenuItem
            || suffix instanceof ClearSuffixItem) {
          items[31] = suffix.createItem(player);
        } else if (suffix instanceof PageSuffixItem) {
          PageSuffixItem page = (PageSuffixItem) suffix;
          items[page.next ? 35 : 27] = page.createItem(player);
        } else {
          items[i] = suffix.createItem(player);
          i++;
        }
      }
      return items;
    }
  }

  public static class SuffixItem implements InventoryMenuItem {
    private final Suffix suffix;
    private final boolean apply;

    public SuffixItem(Suffix suffix, boolean apply) {
      this.suffix = suffix;
      this.apply = apply;
    }

    public String getPermission() {
      return Permissions.GROUP + "." + suffix.getId();
    }

    @Override
    public Component getName() {
      Component component = TextComponent.of(suffix.getFlair().getSuffix(), suffix.color);
      if (suffix.premium) {
        component.decoration(TextDecoration.BOLD, TextDecoration.State.TRUE);
      }
      return component;
    }

    @Override
    public ChatColor getColor() {
      return ChatColor.valueOf(suffix.color.toString().toUpperCase());
    }

    @Override
    public List<String> getLore(MatchPlayer player) {
      Component component = player.getName(NameStyle.COLOR).append(getName());
      return Lists.newArrayList(TextTranslations.translateLegacy(component, player.getBukkit()));
    }

    @Override
    public Material getMaterial(MatchPlayer player) {
      return Material.getMaterial(351);
    }

    @Override
    public ItemStack createItem(MatchPlayer player) {
      PlayerData data = player.getPlayerData();
      JSONArray array;
      if (data.getData().has("suffixes")) array = data.getData().getJSONArray("suffixes");
      else array = new JSONArray();
      boolean has = false;
      if (!apply) {
        for (Object o : array) {
          String name = (String) o;
          if (suffix.getId().equals(name)) {
            has = true;
            break;
          }
        }
      }
      ItemStack stack =
          new ItemStack(
              has ? Material.REDSTONE : getMaterial(player),
              1,
              has ? 0 : DyeColor.SILVER.getData());
      ItemMeta meta = stack.getItemMeta();
      meta.setDisplayName(
          getColor() + TextTranslations.translateLegacy(getName(), player.getBukkit()));
      List<String> lore = getLore(player);
      if (has) {
        lore.add(ChatColor.RED + "You already own this item!");
      }
      meta.setLore(lore);
      meta.addItemFlags(ItemFlag.values());
      stack.setItemMeta(meta);
      return stack;
    }

    @Override
    public void onInventoryClick(InventoryMenu menu, MatchPlayer player, ClickType clickType) {
      if (Bukkit.getPluginManager().getPlugin("Events") != null) {
        player.sendWarning(TextComponent.of("Coins are currently disabled!"));
        return;
      }
      if (apply) {
        applySuffix(player, suffix);
        return;
      }
      Coins coins = player.getCoins();
      if (coins.getCoins() < COST) {
        player.sendWarning(TextComponent.of("You do not have enough coins!"));
        return;
      }
      if (giveSuffix(player.getPlayerData(), suffix)) {
        coins.removeCoins(COST);
        if (attachmentMap.containsKey(player.getId())) {
          attachmentMap.get(player.getId()).remove();
        }
        PermissionAttachment attachment =
            player.getBukkit().addAttachment(PGM.get(), getPermission(), true);
        attachmentMap.put(player.getId(), attachment);
        player.sendMessage(
            TextComponent.of("You have bought", TextColor.GREEN)
                .append(getName())
                .append(TextComponent.of(" for " + COST + " coins", TextColor.GREEN)));
        match.callEvent(new NameDecorationChangeEvent(player.getId()));
        player.getBukkit().closeInventory();
      }
    }
  }

  public static void giveSuffixes(PlayerData data, List<Suffix> suffixes) {
    JSONArray array;
    if (data.getData().has("suffixes")) array = data.getData().getJSONArray("suffixes");
    else array = new JSONArray();
    for (Suffix suffix : suffixes) {
      boolean has = false;
      for (Object o : array) {
        String name = (String) o;
        if (suffix.getId().equals(name)) {
          has = true;
          break;
        }
      }
      if (has) continue;
      array.put(suffix.getId());
    }
    data.setData("suffixes", array);
    data.setData("current_suffix", suffixes.get(0).getPermissionString());
  }

  public static boolean giveSuffix(PlayerData data, Suffix suffix) {
    JSONArray array;
    if (data.getData().has("suffixes")) array = data.getData().getJSONArray("suffixes");
    else array = new JSONArray();
    boolean has = false;
    for (Object o : array) {
      String name = (String) o;
      if (suffix.getId().equals(name)) {
        has = true;
        break;
      }
    }
    if (has) return false;
    array.put(suffix.getId());
    data.setData("suffixes", array);
    data.setData("current_suffix", suffix.getPermissionString());
    return true;
  }

  public static void applySuffix(PlayerData data, Suffix suffix) {
    data.setData("current_suffix", suffix.getPermissionString());
  }

  public static void applySuffix(MatchPlayer player, Suffix suffix) {
    applySuffix(player.getPlayerData(), suffix);
    if (attachmentMap.containsKey(player.getId())) {
      attachmentMap.get(player.getId()).remove();
    }
    PermissionAttachment attachment =
        player.getBukkit().addAttachment(PGM.get(), suffix.getPermissionString(), true);
    attachmentMap.put(player.getId(), attachment);
    player.sendMessage(
        TextComponent.of("You have selected", TextColor.GREEN)
            .append(TextComponent.of(suffix.getFlair().getSuffix(), suffix.color)));
    match.callEvent(new NameDecorationChangeEvent(player.getId()));
    player.getBukkit().closeInventory();
  }

  public static class PageSuffixItem implements InventoryMenuItem {
    private final boolean next;
    private final int pages;
    private final int page;

    public PageSuffixItem(int page, int pages, boolean next) {
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
      if (menu instanceof SuffixesMenu) {
        SuffixesMenu suffixesMenu = (SuffixesMenu) menu;
        if (clickType.isLeftClick()) {
          List<SuffixesMenu> menuList =
              suffixesMenu.getPlayer() != null
                  ? OWNED_MENUS.get(suffixesMenu.getPlayer().getId())
                  : MENUS;
          int index = menuList.indexOf(suffixesMenu);
          menu.remove(player);
          if (next) {
            suffixesMenu = menuList.get(index + 1);
          } else {
            suffixesMenu = menuList.get(index - 1);
          }
          suffixesMenu.display(player);
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

  public static class ClearSuffixItem implements InventoryMenuItem {

    @Override
    public Component getName() {
      return TextComponent.of("Clear", TextColor.WHITE);
    }

    @Override
    public ChatColor getColor() {
      return ChatColor.WHITE;
    }

    @Override
    public List<String> getLore(MatchPlayer player) {
      return Lists.newArrayList(ChatColor.GRAY + "Clear your current suffix");
    }

    @Override
    public Material getMaterial(MatchPlayer player) {
      return Material.BARRIER;
    }

    @Override
    public void onInventoryClick(InventoryMenu menu, MatchPlayer player, ClickType clickType) {
      PlayerData data = player.getPlayerData();
      PermissionAttachment attachment = attachmentMap.get(player.getId());
      if (attachment == null) return;
      attachmentMap.remove(player.getId()).remove();
      data.removeData("current_suffix");
      match.callEvent(new NameDecorationChangeEvent(player.getId()));
      player.getBukkit().closeInventory();
      player.sendMessage(TextComponent.of("You have cleared your suffix!", TextColor.GREEN));
    }
  }

  public static class Suffix implements Config.Group {
    private final String name;
    private final String key;
    public boolean premium;
    private final TextColor color;
    private final Config.Flair flair;
    private final Permission permission;

    public Suffix(String name, String suffix, TextColor color) {
      this.name = name.toLowerCase() + "_" + color.toString();
      this.key = name.replace('_', ' ');
      this.color = color;
      flair = new SuffixFlair(suffix, color, this);
      permission = Permissions.register(new Permission(Permissions.GROUP + "." + getId()));
      premium = false;
    }

    public String getPermissionString() {
      return Permissions.GROUP + "." + getId();
    }

    public TextColor getColor() {
      return color;
    }

    public String getKey() {
      return key;
    }

    @Override
    public String getId() {
      return name;
    }

    @Override
    public Config.Flair getFlair() {
      return flair;
    }

    @Override
    public Permission getPermission() {
      return permission;
    }

    @Override
    public Permission getObserverPermission() {
      return Permissions.DEFAULT;
    }

    @Override
    public Permission getParticipantPermission() {
      return Permissions.DEFAULT;
    }

    private static class SuffixFlair implements Config.Flair {
      private final String suffix;
      private final TextColor color;
      private final Suffix suff;

      public SuffixFlair(String suffix, TextColor color, Suffix suff) {
        this.suffix = suffix;
        this.color = color;
        this.suff = suff;
      }

      @Override
      public String getPrefix() {
        return null;
      }

      @Override
      public String getSuffix() {
        return (suff.premium ? " " + ChatColor.BOLD : " ")
            + ChatColor.valueOf(color.toString().toUpperCase())
            + suffix;
      }

      @Override
      public String getDescription() {
        return null;
      }

      @Override
      public String getDisplayName() {
        return null;
      }

      @Override
      public String getClickLink() {
        return null;
      }

      @Override
      public String getMessageColor() {
        return null;
      }

      @Override
      public Component getPrefixOverride() {
        return null;
      }

      @Override
      public Component getSuffixOverride() {
        return null;
      }
    }
  }
}
