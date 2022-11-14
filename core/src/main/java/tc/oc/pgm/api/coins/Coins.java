package tc.oc.pgm.api.coins;

import java.util.UUID;

public interface Coins {
  /**
   * Get the unique {@link UUID} of the player.
   *
   * @return The unique {@link UUID}.
   */
  UUID getId();

  /**
   * Gets the amount of coins the player has
   *
   * @return long
   */
  long getCoins();

  /**
   * Sets the amount of coins for the player
   *
   * @param amount the amount to set
   */
  void setCoins(long amount);

  /**
   * Adds the stated amount of coins to the player
   *
   * @param amount the amount to add
   */
  void addCoins(long amount);

  /**
   * Removes the stated amount of coins from the player
   *
   * @param amount the amount to remove
   */
  void removeCoins(long amount);
}
