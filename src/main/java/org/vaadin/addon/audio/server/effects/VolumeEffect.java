package org.vaadin.addon.audio.server.effects;

import org.vaadin.addon.audio.server.Effect;
import org.vaadin.addon.audio.shared.SharedEffect;
import org.vaadin.addon.audio.shared.SharedEffectProperty;

import java.util.ArrayList;
import java.util.List;

public class VolumeEffect extends Effect {

    private double gain;

    public VolumeEffect() {
        super();
    }

    public void setGain(double gain) {
        this.gain = gain;
    }

    public double getGain() {
        return gain;
    }

    @Override
    public SharedEffect getSharedEffectObject() {
        SharedEffect shared = new SharedEffect(getID(), SharedEffect.EffectName.VolumeEffect);
        List<SharedEffectProperty> props = new ArrayList<>();
        props.add(new SharedEffectProperty(SharedEffectProperty.PropertyName.Gain, getGain() + ""));
        shared.setProperties(props);
        return shared;
    }

}
