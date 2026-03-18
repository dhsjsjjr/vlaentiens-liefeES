package com.heartbreak;

import com.heartbreak.command.HeartbreakCommands;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HeartbreakMod implements ModInitializer {
    public static final String MOD_ID = "heartbreaksmp";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        HeartbreakCommands.register();
        LOGGER.info("Heartbreak SMP initialized! Use /heartbreak start to begin.");
    }
}
