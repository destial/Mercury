package tc.oc.pgm.db;

import java.util.UUID;
import tc.oc.pgm.api.coins.Coins;

public class CoinsImpl implements Coins {

  private final UUID id;
  private long coins;

  CoinsImpl(UUID id, long amount) {
    this.id = id;
    this.coins = amount;
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public long getCoins() {
    return coins;
  }

  // @Override
  public void setCoins(long amount) {
    this.coins = amount;
  }

  @Override
  public void addCoins(long amount) {
    setCoins(this.coins + amount);
  }

  @Override
  public void removeCoins(long amount) {
    setCoins(this.coins - amount);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Coins)) return false;
    return getId().equals(((Coins) o).getId());
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return id != null ? ("{ id=" + id.toString() + ", coins=" + coins + " }") : "{}";
  }
}
