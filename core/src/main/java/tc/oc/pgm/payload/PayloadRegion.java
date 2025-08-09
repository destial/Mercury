package tc.oc.pgm.payload;

import java.util.Iterator;
import java.util.Random;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;
import tc.oc.pgm.regions.AbstractRegion;
import tc.oc.pgm.regions.Bounds;

/** This is a region that is not immutable. The origin point of the cylinder can move. */
public class PayloadRegion extends AbstractRegion {

  private final Supplier<Vector> base;
  private final double radius;
  private final double radiusSq;

  public PayloadRegion(Supplier<Vector> base, double radius) {
    this.base = base;
    this.radius = radius;
    this.radiusSq = Math.pow(radius, 2);
  }

  @Override
  public boolean contains(Vector point) {
    Vector base = this.base.get();

    return point.getY() >= (base.getY() - 2.5)
        && point.getY() <= (base.getY() + 2.5)
        && Math.pow(point.getX() - base.getX(), 2) + Math.pow(point.getZ() - base.getZ(), 2)
            < this.radiusSq;
  }

  @Override
  public boolean contains(Location point) {
    return contains(point.toVector());
  }

  @Override
  public boolean contains(BlockVector pos) {
    return contains(new Vector(pos.getX(), pos.getY(), pos.getZ()));
  }

  @Override
  public boolean contains(Block block) {
    return contains(block.getLocation());
  }

  @Override
  public boolean contains(BlockState block) {
    return contains(block.getLocation());
  }

  @Override
  public boolean contains(Entity entity) {
    return contains(entity.getLocation());
  }

  @Override
  public boolean enters(Location from, Location to) {
    return !contains(from) && contains(to);
  }

  @Override
  public boolean enters(Vector from, Vector to) {
    return !contains(from) && contains(to);
  }

  @Override
  public boolean exits(Location from, Location to) {
    return contains(from) && !contains(to);
  }

  @Override
  public boolean exits(Vector from, Vector to) {
    return contains(from) && !contains(to);
  }

  @Override
  public boolean canGetRandom() {
    return false;
  }

  @Override
  public Vector getRandom(Random random) {
    return this.base.get();
  }

  @Override
  public boolean isBlockBounded() {
    return !Double.isInfinite(radius);
  }

  @Override
  public Bounds getBounds() {
    Vector base = this.base.get();
    return new Bounds(
        new Vector(base.getX() - this.radius, base.getY() - 2.5, base.getZ() - this.radius),
        new Vector(base.getX() + this.radius, base.getY() + 2.5, base.getZ() + this.radius));
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public Iterator<BlockVector> getBlockVectorIterator() {
    return null;
  }

  @Override
  public Iterable<BlockVector> getBlockVectors() {
    return null;
  }

  @Override
  public String toString() {
    return "PayloadRegion{base=[" + this.base.get() + "],radiusSq=" + this.radiusSq + "}";
  }
}
