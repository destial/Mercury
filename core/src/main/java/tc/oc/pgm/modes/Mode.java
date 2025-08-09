package tc.oc.pgm.modes;

import java.time.Duration;
import javax.annotation.Nullable;
import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import org.bukkit.material.MaterialData;
import tc.oc.pgm.api.filter.Filter;
import tc.oc.pgm.features.SelfIdentifyingFeatureDefinition;

public class Mode extends SelfIdentifyingFeatureDefinition {

  private final MaterialData material;
  private final Duration after;
  private final @Nullable String name;
  private final @Nullable Filter filter;
  private final Component componentName;
  private final Duration showBefore;

  public Mode(
      final @Nullable String id,
      final MaterialData material,
      final Duration after,
      Duration showBefore) {
    this(id, material, after, null, null, showBefore);
  }

  public Mode(
      final @Nullable String id,
      final MaterialData material,
      final Duration after,
      final @Nullable String name,
      final @Nullable Filter filter,
      Duration showBefore) {
    super(id);
    this.material = material;
    this.after = after;
    this.name = name;
    this.componentName =
        TextComponent.of(name != null ? name : getPreformattedMaterialName(), TextColor.RED);
    this.showBefore = showBefore;
    this.filter = filter;
  }

  public MaterialData getMaterialData() {
    return this.material;
  }

  public Component getComponentName() {
    return componentName;
  }

  public String getPreformattedMaterialName() {
    return ModeUtils.formatMaterial(this.material);
  }

  public Duration getAfter() {
    return this.after;
  }

  public Duration getShowBefore() {
    return this.showBefore;
  }

  public @Nullable String getName() {
    return this.name;
  }

  @Nullable
  public Filter getFilter() {
    return filter;
  }
}
