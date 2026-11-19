package com.terminaldetector.drmd.flight;

/** Shared environmental coefficients only; both existing movement integrators keep their state. */
public record FlightEnvironment(float accelerationMultiplier,float dragCoefficient,float speedMultiplier) {
    public static FlightEnvironment resolve(float accelerator,float speed,float drag,float thrustScale,float airDrag) {
        // Preserve the authoritative server's float multiplication/interpolation order.
        return new FlightEnvironment(accelerator*thrustScale,drag*airDrag,
            speed*(1f+(1f-airDrag)*(1.15f-1f)));
    }
}
