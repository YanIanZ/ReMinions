package com.willfp.libreforge;

import com.willfp.libreforge.conditions.ConditionList;
import com.willfp.libreforge.effects.EffectList;

/** Compile-time stub — libreforge ships as a plugin jar, not a Maven artifact. */
public interface Holder {
    EffectList getEffects();
    ConditionList getConditions();
}
