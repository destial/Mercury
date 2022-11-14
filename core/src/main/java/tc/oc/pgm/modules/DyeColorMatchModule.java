package tc.oc.pgm.modules;

import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchModule;
import tc.oc.pgm.api.match.MatchScope;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.events.ListenerScope;
import tc.oc.pgm.kits.ApplyItemKitEvent;

@ListenerScope(MatchScope.LOADED)
public class DyeColorMatchModule implements MatchModule, Listener {
  public DyeColorMatchModule(Match match) {}

  @EventHandler(ignoreCancelled = true)
  public void onApplyItemKit(ApplyItemKitEvent e) {
    for (ItemStack slot : e.getSlotItems().values()) {
      if (isReplaceable(slot)) replaceColor(slot, e.getPlayer());
    }
    for (ItemStack free : e.getFreeItems()) {
      if (isReplaceable(free)) replaceColor(free, e.getPlayer());
    }
  }

  public boolean isReplaceable(ItemStack item) {
    return item.getType().equals(Material.WOOL)
        || item.getType().equals(Material.STAINED_CLAY)
        || item.getType().equals(Material.STAINED_GLASS)
        || item.getType().equals(Material.STAINED_GLASS_PANE);
  }

  public void replaceColor(ItemStack item, MatchPlayer player) {
    item.setDurability(getDye(player.getParty().getColor()).getWoolData());
    // MaterialData data =
    // item.getType().getNewData(getDye(player.getParty().getColor()).getData());
    // item.setData(data);
  }

  public DyeColor getDye(ChatColor color) {
    switch (color) {
      case GREEN:
        return DyeColor.LIME;
      case DARK_GREEN:
        return DyeColor.GREEN;
      case DARK_GRAY:
        return DyeColor.GRAY;
      case DARK_BLUE:
        return DyeColor.BLUE;
      case RED:
      case DARK_RED:
        return DyeColor.RED;
      case YELLOW:
        return DyeColor.YELLOW;
      case GOLD:
        return DyeColor.ORANGE;
      case DARK_AQUA:
      case AQUA:
        return DyeColor.CYAN;
      case BLUE:
        return DyeColor.LIGHT_BLUE;
      case BLACK:
        return DyeColor.BLACK;
      case GRAY:
        return DyeColor.SILVER;
      case DARK_PURPLE:
        return DyeColor.PURPLE;
      case LIGHT_PURPLE:
        return DyeColor.PINK;
      default:
        return DyeColor.WHITE;
    }
  }
}
