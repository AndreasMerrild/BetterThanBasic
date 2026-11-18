package CP;

public class cp {

    public cp() {
    }

    /**
     * Establishes PMax from the best one-second maximal power.
     *
     * @param oneSecondPower best average power over one second, in watts
     * @return PMax in watts
     */
    public double calcPMax(double oneSecondPower) {
        if (!Double.isFinite(oneSecondPower) || oneSecondPower <= 0) {
            throw new IllegalArgumentException(
                    "One-second power must be a finite, positive value.");
        }
        return oneSecondPower;
    }

    /**
     * Calculates W' from an average power sustained for a known duration.
     *
     * @param criticalPower critical power in watts
     * @param durationPower average power over the duration, in watts
     * @param durationSeconds duration of the effort, in seconds
     * @return W' in joules
     */
    public double calcWPrime(double criticalPower, double durationPower,
                             double durationSeconds) {
        if (!Double.isFinite(criticalPower) || !Double.isFinite(durationPower)
                || !Double.isFinite(durationSeconds)
                || criticalPower <= 0 || durationPower <= criticalPower
                || durationSeconds <= 0) {
            throw new IllegalArgumentException(
                    "Critical power and duration power must be finite, positive values, "
                            + "duration power must exceed critical power, "
                            + "and duration must be positive.");
        }
        return (durationPower - criticalPower) * durationSeconds;
    }

    /**
     * Calculates critical power from the best three-minute and twenty-minute
     * powers using the two-parameter work-capacity model.
     *
     * @param twenty best average power for twenty minutes, in watts
     * @param three best average power for three minutes, in watts
     * @param wprime retained for API compatibility; it is not needed when two
     *               maximal efforts are provided
     * @return critical power in watts
     */
    public double calcCP(double twenty, double three, int wprime) {
        if (!Double.isFinite(twenty) || !Double.isFinite(three)
                || twenty <= 0 || three <= 0 || three <= twenty) {
            throw new IllegalArgumentException(
                    "Twenty-minute power and three-minute power must be finite, positive values, "
                            + "and three-minute power must exceed twenty-minute power.");
        }

        // P = CP + W'/t, so two efforts give:
        // CP = (P20 * 1200 - P3 * 180) / (1200 - 180).
        return (twenty * 1200.0 - three * 180.0) / (1200.0 - 180.0);
    }
}
