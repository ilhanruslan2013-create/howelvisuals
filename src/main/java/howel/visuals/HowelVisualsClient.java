package com.howel.visuals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;

public class HowelVisualsClient implements ClientModInitializer {
    public static PlayerEntity activeTarget = null;
    public static KeyBinding openGuiKey;
    
    // Модули
    public static boolean killauraEnabled = true;
    public static boolean autoSprintEnabled = true;
    public static boolean targetHudEnabled = true;
    public static boolean espEnabled = true;
    
    public static int attackKey = GLFW.GLFW_KEY_R;

    @Override
    public void onInitializeClient() {
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.howelvisuals.gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.howelvisuals"
        ));

        TargetHudRenderer.init();
        TargetEspRenderer.init();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null || mc.world == null) return;

            // Открытие GUI на правый шифт
            if (openGuiKey.wasPressed()) {
                mc.setScreen(new HowelGuiScreen());
            }

            // AutoSprint (Авто-спринт для FunTime / SpaceTime)
            if (autoSprintEnabled && mc.player.input.movementForward > 0 && !mc.player.isSneaking() && !mc.player.horizontalCollision) {
                mc.player.setSprinting(true);
            }

            // KillAura с фокусом на ближайшего игрока (обход античита)
            if (killauraEnabled) {
                activeTarget = mc.world.getEntitiesByClass(PlayerEntity.class, mc.player.getBoundingBox().expand(4.2), e -> e != mc.player && !e.isSpectator() && e.isAlive())
                        .stream()
                        .min(Comparator.comparingDouble(mc.player::distanceTo))
                        .orElse(null);

                if (activeTarget != null && InputUtil.isKeyPressed(mc.getWindow().getHandle(), attackKey)) {
                    if (mc.player.distanceTo(activeTarget) <= 4.0) {
                        mc.interactionManager.attackEntity(mc.player, activeTarget);
                        mc.player.swingHand(Hand.MAIN_HAND);
                    }
                }
            } else {
                activeTarget = null;
            }
        });
    }
}
