package tc.oc.pgm.api.community.achievement;

public enum AchievementType {
  KILLS(10, 50, 100, 300, 500, 1000),
  BOW_KILLS(10, 50, 100, 300, 500, 1000),
  DEATHS(10, 50, 100, 300, 500, 1000),
  KILLSTREAK(10, 50, 100, 300, 500, 1000),
  ARROWS(50, 150, 500, 1000, 15000, 20000),
  OBJECTIVES(3, 10, 20, 50, 100, 150, 300, 500, 750, 1000),
  WINS(5, 10, 50, 75, 100, 250, 500, 800, 1000),
  MYPS(5, 10, 50, 75, 100, 250, 500, 800, 1000);

  private final int[] ranges;

  AchievementType(int... ranges) {
    this.ranges = ranges;
  }

  public int[] getRanges() {
    return ranges;
  }
}
