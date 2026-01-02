package me.cortex.nvidium.config;

import me.cortex.nvidium.Nvidium;

public class NvidiumConfigStore {
    private final NvidiumConfig config;

    public NvidiumConfigStore() {
        config = Nvidium.config;
    }

    public NvidiumConfig getData() {
        return config;
    }

    public void save() {
        config.save();
    }
}
