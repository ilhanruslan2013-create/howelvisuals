package com.howel.visuals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;

public class HowelVisualsClient implements ClientModInitializer {
    public static PlayerEntity activeTarget = null;
    public static KeyBinding openGuiKey;
    
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

        // Рендер Target HUD на экране
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (!targetHudEnabled) return;
            PlayerEntity target = activeTarget;
            if (target != null) {
                int width = context.getScaledWindowWidth();
                int height = context.getScaledWindowHeight();
                int x = (width / 2) + 15;
                int y = (height / 2) - 15;

                context.fill(x, y, x + 140, y + 48, 0xAA0B0D13);
                context.fill(x, y, x + 140, y + 2, 0xFFFF5555);
                context.drawText(MinecraftClient.getInstance().textRenderer, target.getName().getString(), x + 8, y + 8, 0xFFFFFFFF, true);
                context.drawText(MinecraftClient.getInstance().textRenderer, "HP: " + (int)target.getHealth(), x + 8, y + 26, 0xFFFF5555, false);
            }
        });

        // Рендер ESP Box в мире
        WorldRenderEvents.LAST.register(context -> {
            if (!espEnabled) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            PlayerEntity target = activeTarget;
            if (mc.player != null && target != null) {
                VertexConsumerProvider.Immediate consumers = context.consumers();
                if (consumers == null) return;

                Box box = target.getBoundingBox().offset(
                        -mc.gameRenderer.getCamera().getPos().x, 
                        -mc.gameRenderer.getCamera().getPos().y, 
                        -mc.gameRenderer.getCamera().getPos().z
                );
                WorldRenderer.drawBox(context.matrixStack(), consumers.getBuffer(RenderLayer.getLines()), box, 1.0f, 0.2f, 0.2f, 1.0f);
            }
        });

        // Основной цикл тиков (KillAura, AutoSprint, открытие GUI)
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null || mc.world == null) return;

            if (openGuiKey.wasPressed()) {
                mc.setScreen(new HowelGuiScreen());
            }

            if (autoSprintEnabled && mc.player.input.movementForward > 0 && !mc.player.isSneaking() && !mc.player.horizontalCollision) {
                mc.player.setSprinting(true);
            }

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

    // Встроенный класс для GUI меню в стиле Vexide / Delta
    public static class HowelGuiScreen extends Screen {
        private int selectedTab = 0;
        private final String[] tabs = {"Combat", "Visuals", "Player", "Settings"};

        public HowelGuiScreen() {
            super(Text.literal("Howel Client GUI"));
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context, mouseX, mouseY, delta);

            int guiWidth = 420;
            int guiHeight = 270;
            int x = (this.width - guiWidth) / 2;
            int y = (this.height - guiHeight) / 2;

            context.fill(x, y, x + guiWidth, y + guiHeight, 0xFF12141C);
            context.fill(x, y, x + guiWidth, y + 36, 0xFF1A1D27);
            context.fill(x, y + 36, x + 120, y + guiHeight, 0xFF161821);

            context.drawText(this.textRenderer, "HOWEL CLIENT", x + 16, y + 13, 0xFFFF5555, true);
            context.drawText(this.textRenderer, "v1.21.4 • Private", x + 16, y + 24, 0xFF6C7086, false);

            int tabY = y + 50;
            for (int i = 0; i < tabs.length; i++) {
                boolean selected = (i == selectedTab);
                if (selected) {
                    context.fill(x + 10, tabY - 2, x + 110, tabY + 20, 0xFF232733);
                    context.drawText(this.textRenderer, "» " + tabs[i], x + 18, tabY + 5, 0xFFFFFFFF, true);
                } else {
                    context.drawText(this.textRenderer, tabs[i], x + 18, tabY + 5, 0xFF7C8196, false);
                }
                tabY += 28;
            }

            int contentX = x + 135;
            int contentY = y + 50;

            if (selectedTab == 0) {
                renderToggle(context, contentX, contentY, "KillAura", killauraEnabled);
            } else if (selectedTab == 1) {
                renderToggle(context, contentX, contentY, "Target HUD", targetHudEnabled);
                renderToggle(context, contentX, contentY + 45, "ESP Box", espEnabled);
            } else if (selectedTab == 2) {
                renderToggle(context, contentX, contentY, "AutoSprint", autoSprintEnabled);
            } else if (selectedTab == 3) {
                context.drawText(this.textRenderer, "Config: Default Profile", contentX, contentY, 0xFFFFFFFF, true);
                context.drawText(this.textRenderer, "Server bypass: FunTime / SpaceTime", contentX, contentY + 15, 0xFF888899, false);
            }

            super.render(context, mouseX, mouseY, delta);
        }

        private void renderToggle(DrawContext context, int x, int y, String name, boolean toggled) {
            context.fill(x, y, x + 265, y + 36, 0xFF1C1F2B);
            context.drawText(this.textRenderer, name, x + 12, y + 12, 0xFFFFFFFF, true);

            int btnX = x + 210;
            int btnY = y + 10;
            context.fill(btnX, btnY, btnX + 42, btnY + 16, toggled ? 0xFF3BA55D : 0xFF2A2D3D);
            context.fill(btnX + (toggled ? 24 : 2), btnY + 2, btnX + (toggled ? 40 : 18), btnY + 14, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int x = (this.width - 420) / 2;
            int y = (this.height - 270) / 2;

            int tabY = y + 50;
            for (int i = 0; i < tabs.length; i++) {
                if (mouseX >= x + 10 && mouseX <= x + 110 && mouseY >= tabY - 2 && mouseY <= tabY + 20) {
                    selectedTab = i;
                    return true;
                }
                tabY += 28;
            }

            int contentX = x + 135;
            int contentY = y + 50;
            if (selectedTab == 0) {
                if (mouseX >= contentX && mouseX <= contentX + 265 && mouseY >= contentY && mouseY <= contentY + 36) {
                    killauraEnabled = !killauraEnabled;
                }
            } else if (selectedTab == 1) {
                if (mouseX >= contentX && mouseX <= contentX + 265 && mouseY >= contentY && mouseY <= contentY + 36) {
                    targetHudEnabled = !targetHudEnabled;
                }
                if (mouseX >= contentX && mouseX <= contentX + 265 && mouseY >= contentY + 45 && mouseY <= contentY + 81) {
                    espEnabled = !espEnabled;
                }
            } else if (selectedTab == 2) {
                if (mouseX >= contentX && mouseX <= contentX + 265 && mouseY >= contentY && mouseY <= contentY + 36) {
                    autoSprintEnabled = !autoSprintEnabled;
                }
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.close();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean shouldPause() {
            return false;
        }
    }
}
