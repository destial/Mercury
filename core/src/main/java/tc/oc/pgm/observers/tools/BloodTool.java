package tc.oc.pgm.observers.tools;

import com.google.common.collect.Lists;
import java.util.List;
import net.kyori.text.Component;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.format.TextColor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.setting.SettingKey;
import tc.oc.pgm.api.setting.SettingValue;
import tc.oc.pgm.menu.InventoryMenu;
import tc.oc.pgm.menu.InventoryMenuItem;
import tc.oc.pgm.util.text.TextTranslations;

public class BloodTool implements InventoryMenuItem {
  private static final String TRANSLATION_KEY = "setting.blood.";

  @Override
  public Component getName() {
    return TranslatableComponent.of("setting.blood");
  }

  @Override
  public ChatColor getColor() {
    return ChatColor.RED;
  }

  @Override
  public List<String> getLore(MatchPlayer player) {
    Component bloodState =
        TranslatableComponent.of(
            hasBloodEffect(player) ? "misc.on" : "misc.off",
            hasBloodEffect(player) ? TextColor.GREEN : TextColor.RED);
    Component lore = TranslatableComponent.of(TRANSLATION_KEY + "lore", TextColor.GRAY, bloodState);
    return Lists.newArrayList(TextTranslations.translateLegacy(lore, player.getBukkit()));
  }

  @Override
  public Material getMaterial(MatchPlayer player) {
    return hasBloodEffect(player) ? Material.REDSTONE : Material.SULPHUR;
  }

  @Override
  public void onInventoryClick(InventoryMenu menu, MatchPlayer player, ClickType clickType) {
    toggleBloodEffect(player);
    menu.refreshWindow(player);
  }

  public boolean hasBloodEffect(MatchPlayer player) {
    return player.getSettings().getValue(SettingKey.BLOOD).equals(SettingValue.BLOOD_ON);
  }

  public void toggleBloodEffect(MatchPlayer player) {
    if (hasBloodEffect(player)) {
      player.getSettings().setValue(SettingKey.BLOOD, SettingValue.BLOOD_OFF);
    } else {
      player.getSettings().setValue(SettingKey.BLOOD, SettingValue.BLOOD_ON);
    }
  }
}
