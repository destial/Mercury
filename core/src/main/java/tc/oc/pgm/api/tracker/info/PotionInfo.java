package tc.oc.pgm.api.tracker.info;

import org.bukkit.potion.PotionEffectType;

public interface PotionInfo extends PhysicalInfo, DamageInfo {
  PotionEffectType getPotionEffect();
}
