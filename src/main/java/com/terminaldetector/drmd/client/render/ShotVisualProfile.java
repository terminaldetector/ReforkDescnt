package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.weapon.projectile.ProjectileVisual;

/** Client presentation only. Durations are ticks and geometry is measured in blocks. */
record ShotVisualProfile(float radius, float trail, float speedTrail, float pulse, Flash muzzle, Flash impact) {
    record Flash(int ticks, float radius, float length, float bloom, float recoil) {}

    private static final ShotVisualProfile LASER = new ShotVisualProfile(.105f, .36f, .32f, .06f,
            new Flash(3, .16f, .32f, .25f, .8f), new Flash(5, .20f, .12f, .55f, 0));
    private static final ShotVisualProfile SLUG = new ShotVisualProfile(.050f, .25f, .44f, 0,
            new Flash(2, .11f, .22f, .1f, .5f), new Flash(4, .15f, .09f, .38f, 0));
    private static final ShotVisualProfile VULCAN = new ShotVisualProfile(.043f, .20f, .39f, 0,
            new Flash(2, .10f, .17f, .1f, .4f), new Flash(4, .13f, .09f, .32f, 0));
    private static final ShotVisualProfile GATLING = new ShotVisualProfile(.057f, .19f, .49f, .05f,
            new Flash(2, .12f, .26f, .12f, .35f), new Flash(5, .14f, .11f, .28f, 0));
    private static final ShotVisualProfile HELIX = new ShotVisualProfile(.068f, .28f, .34f, .09f,
            new Flash(3, .13f, .29f, .15f, .65f), new Flash(5, .18f, .13f, .31f, 0));
    private static final ShotVisualProfile PLASMA = new ShotVisualProfile(.22f, .48f, .20f, .13f,
            new Flash(4, .17f, .26f, .2f, .9f), new Flash(8, .23f, .14f, .80f, 0));
    private static final ShotVisualProfile FUSION = new ShotVisualProfile(.30f, .57f, .29f, .22f,
            new Flash(5, .22f, .37f, .3f, 1f), new Flash(10, .31f, .19f, 1.15f, 0));
    private static final ShotVisualProfile BURN = new ShotVisualProfile(.093f, .43f, .37f, .14f,
            new Flash(3, .14f, .27f, .26f, .75f), new Flash(6, .22f, .15f, .68f, 0));
    private static final ShotVisualProfile GRAVITY = new ShotVisualProfile(.28f, .60f, .16f, .18f,
            new Flash(4, .20f, .24f, .42f, 1f), new Flash(8, .27f, .16f, .94f, 0));
    private static final ShotVisualProfile DRILL = new ShotVisualProfile(.16f, .32f, .18f, .11f,
            new Flash(4, .15f, .31f, .22f, .7f), new Flash(7, .22f, .13f, .73f, 0));
    private static final ShotVisualProfile ROCKET = new ShotVisualProfile(.12f, .35f, .19f, .19f,
            new Flash(5, .18f, .51f, .14f, .9f), new Flash(9, .30f, .21f, 1.0f, 0));
    private static final ShotVisualProfile MINE = new ShotVisualProfile(.16f, .15f, .10f, .19f,
            new Flash(4, .17f, .18f, .33f, .8f), new Flash(9, .28f, .15f, .83f, 0));

    static ShotVisualProfile of(ProjectileVisual visual) {
        return switch (visual) {
            case VULCAN -> VULCAN;
            case GATLING -> GATLING;
            case HELIX -> HELIX;
            case BURN -> BURN;
            default -> switch (visual.family()) {
                case LIGHT -> LASER;
                case BALLISTIC -> SLUG;
                case PLASMA -> PLASMA;
                case FUSION -> FUSION;
                case DRILL -> DRILL;
                case GRAVITY -> GRAVITY;
                case MINE -> MINE;
                case ROCKET -> ROCKET;
            };
        };
    }

    float length(double speed, float scale) {
        return Math.min(3.25f, (trail + (float) speed * speedTrail) * scale);
    }

    float streakLength(double speed, float scale, double eyeDistance) {
        return (float) Math.min(length(speed, scale), eyeDistance * .75);
    }
}
