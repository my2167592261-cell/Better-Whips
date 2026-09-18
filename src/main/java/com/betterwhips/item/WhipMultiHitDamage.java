package com.betterwhips.item;

public final class WhipMultiHitDamage {
    private WhipMultiHitDamage() {}

    public static float scale(float baseDamage, int previouslyResolvedTargets) {
        if (!(baseDamage > 0.0F)) {
            return 0.0F;
        }
        int prior = Math.max(0, previouslyResolvedTargets);
        if (prior >= 30) {
            return 0.0F;
        }
        return Math.scalb(baseDamage, -prior);
    }
}
