package tc.oc.pgm.observers.tools;

import com.google.common.collect.Lists;
import java.util.List;
import net.kyori.text.Component;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.format.TextColor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.setting.SettingKey;
import tc.oc.pgm.api.setting.SettingValue;
import tc.oc.pgm.menu.InventoryMenu;
import tc.oc.pgm.menu.InventoryMenuItem;
import tc.oc.pgm.util.text.TextTranslations;

public class AutoJoinTool implements InventoryMenuItem {
  private static final String TRANSLATION_KEY = "setting.autojoin.";

  @Override
  public Component getName() {
    return TranslatableComponent.of("setting.autojoin");
  }

  @Override
  public ChatColor getColor() {
    return ChatColor.GREEN;
  }

  @Override
  public List<String> getLore(MatchPlayer player) {
    Component joinState =
        TranslatableComponent.of(
            hasAutoJoin(player) ? "misc.on" : "misc.off",
            hasAutoJoin(player) ? TextColor.GREEN : TextColor.RED);
    Component lore = TranslatableComponent.of(TRANSLATION_KEY + "lore", TextColor.GRAY, joinState);
    return Lists.newArrayList(TextTranslations.translateLegacy(lore, player.getBukkit()));
  }

  @Override
  public Material getMaterial(MatchPlayer player) {
    return Material.LEATHER_HELMET;
  }

  @Override
  public ItemStack createItem(MatchPlayer player) {
    ItemStack item = InventoryMenuItem.super.createItem(player);
    LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
    meta.setColor(hasAutoJoin(player) ? Color.GREEN : Color.RED);
    item.setItemMeta(meta);
    return item;
  }

  @Override
  public void onInventoryClick(InventoryMenu menu, MatchPlayer player, ClickType clickType) {
    toggleAutoJoin(player);
    menu.refreshWindow(player);
  }

  public boolean hasAutoJoin(MatchPlayer player) {
    return player.getSettings().getValue(SettingKey.AUTOJOIN) == SettingValue.AUTO_JOIN_ON;
  }

  public void toggleAutoJoin(MatchPlayer player) {
    player
        .getSettings()
        .setValue(
            SettingKey.AUTOJOIN,
            hasAutoJoin(player) ? SettingValue.AUTO_JOIN_OFF : SettingValue.AUTO_JOIN_ON);
  }
}
