package howel.visuals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * HowelVisuals: только визуальные элементы в стиле популярных клиентов
 * (HUD, меню настроек, TargetHUD, броня, клавиши, подсказка крита, эффекты).
 * Ничего не влияет на геймплей и не совершает действий за игрока.
 */
public class HowelVisualsClient implements ClientModInitializer {

    static class Module {
        final String name;
        boolean enabled = true;
        Module(String name) { this.name = name; }
    }

    static final List<Module> MODULES = new ArrayList<>();
    static Module watermark, moduleList, info, targetHud, armorHud, keystrokes, critIndicator, hitFx;

    static final String[] THEME_NAMES = {"Rainbow", "Ocean", "Sunset", "Purple"};
    static final int[][] THEMES = {
        {0, 0},
        {0xFF00C6FF, 0xFF0072FF},
        {0xFFFF512F, 0xFFF09819},
        {0xFFDA22FF, 0xFF9733EE}
    };
    static int theme = 0;

    static KeyBinding guiKey;
    static final Random RNG = Random.create();

    // TargetHUD
    static LivingEntity lastTarget = null;
    static long lastSeen = 0;
    static float animHealth = 0;

    // Вспышка "CRIT" после удара
    static long critFlash = 0;

    @Override
    public void onInitializeClient() {
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.howelvisuals.master", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.howelvisuals"));

        MODULES.add(watermark     = new Module("Watermark"));
        MODULES.add(moduleList    = new Module("Module List"));
        MODULES.add(info          = new Module("Info"));
        MODULES.add(targetHud     = new Module("Target HUD"));
        MODULES.add(armorHud      = new Module("Armor HUD"));
        MODULES.add(keystrokes    = new Module("Keystrokes"));
        MODULES.add(critIndicator = new Module("Crit Indicator"));
        MODULES.add(hitFx         = new Module("Hit Effects"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (guiKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new HowelGuiScreen());
            }
        });

        HudRenderCallback.EVENT.register(HowelVisualsClient::render);

        // Эффекты при ударе игрока (только визуально, удар делает сам игрок)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient) {
                boolean crit = isCritState(player);
                if (crit && critIndicator.enabled) critFlash = System.currentTimeMillis();
                if (hitFx.enabled) {
                    for (int i = 0; i < 14; i++) {
                        world.addParticle(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.CRIT,
                            entity.getX(), entity.getBodyY(0.5), entity.getZ(),
                            (RNG.nextDouble() - 0.5) * 0.5, RNG.nextDouble() * 0.4, (RNG.nextDouble() - 0.5) * 0.5);
                    }
                    if (crit) {
                        for (int i = 0; i < 10; i++) {
                            world.addParticle(ParticleTypes.ENCHANTED_HIT,
                                entity.getX(), entity.getBodyY(0.6), entity.getZ(),
                                (RNG.nextDouble() - 0.5) * 0.8, RNG.nextDouble() * 0.5, (RNG.nextDouble() - 0.5) * 0.8);
                        }
                    }
                }
            }
            return ActionResult.PASS;
        });
    }

    /** Условия, при которых удар будет критическим (как в ванили). Только для отображения. */
    static boolean isCritState(PlayerEntity p) {
        return p.fallDistance > 0.0f
            && !p.isOnGround()
            && !p.isClimbing()
            && !p.isTouchingWater()
            && !p.hasStatusEffect(StatusEffects.BLINDNESS)
            && !p.hasVehicle();
    }

    // ---------- Цвета и темы ----------

    static int accent(int offset) {
        if (theme == 0) {
            float hue = ((System.currentTimeMillis() / 20 + offset) % 360) / 360f;
            return Color.HSBtoRGB(hue, 0.65f, 1f) | 0xFF000000;
        }
        int a = THEMES[theme][0], b = THEMES[theme][1];
        float t = (float) (Math.sin(System.currentTimeMillis() / 400.0 + offset * 0.05) * 0.5 + 0.5);
        int r = (int) MathHelper.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) MathHelper.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) MathHelper.lerp(t, a & 255, b & 255);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    // ---------- Меню настроек (Right Shift) ----------

    public static class HowelGuiScreen extends Screen {
        public HowelGuiScreen() {
            super(Text.literal("HowelVisuals"));
        }

        private static Text label(Module m) {
            return Text.literal(m.name + ": " + (m.enabled ? "§aON" : "§cOFF"));
        }

        private static Text themeLabel() {
            return Text.literal("Theme: " + THEME_NAMES[theme]);
        }

        @Override
        protected void init() {
            int cx = this.width / 2;
            int total = (MODULES.size() + 2) * 24 + 6;
            int y = Math.max(24, this.height / 2 - total / 2);
            for (Module m : MODULES) {
                addDrawableChild(ButtonWidget.builder(label(m), b -> {
                    m.enabled = !m.enabled;
                    b.setMessage(label(m));
                }).dimensions(cx - 75, y, 150, 20).build());
                y += 24;
            }
            y += 6;
            addDrawableChild(ButtonWidget.builder(themeLabel(), b -> {
                theme = (theme + 1) % THEME_NAMES.length;
                b.setMessage(themeLabel());
            }).dimensions(cx - 75, y, 150, 20).build());
            y += 24;
            addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(cx - 75, y, 150, 20).build());
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            super.render(ctx, mouseX, mouseY, delta);
            int total = (MODULES.size() + 2) * 24 + 6;
            int top = Math.max(24, this.height / 2 - total / 2);
            ctx.drawCenteredTextWithShadow(this.textRenderer, "HowelVisuals", this.width / 2, top - 14, accent(0));
        }
    }

    // ---------- HUD ----------

    static void keyBox(DrawContext ctx, net.minecraft.client.font.TextRenderer tr, int x, int y, int w, String label, boolean down) {
        ctx.fill(x, y, x + w, y + 18, down ? 0xB0FFFFFF : 0x80000000);
        ctx.fill(x, y + 17, x + w, y + 18, accent(x));
        int color = down ? 0xFF000000 : 0xFFFFFFFF;
        ctx.drawText(tr, label, x + (w - tr.getWidth(label)) / 2, y + 5, color, false);
    }

    static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        var tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();

        // Watermark
        if (watermark.enabled) {
            String text = "HowelVisuals";
            int w = tr.getWidth(text) + 10;
            ctx.fill(4, 4, 4 + w, 18, 0x90000000);
            ctx.fill(4, 4, 4 + w, 5, accent(0));
            int x = 9;
            for (int i = 0; i < text.length(); i++) {
                String ch = String.valueOf(text.charAt(i));
                ctx.drawTextWithShadow(tr, ch, x, 8, accent(i * 18));
                x += tr.getWidth(ch);
            }
        }

        // Info
        if (info.enabled) {
            String[] lines = {
                "FPS " + mc.getCurrentFps(),
                String.format("XYZ %.1f %.1f %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ()),
                "Facing " + mc.player.getHorizontalFacing().asString()
            };
            int y = 24;
            for (String s : lines) {
                int w = tr.getWidth(s) + 8;
                ctx.fill(4, y, 4 + w, y + 11, 0x80000000);
                ctx.fill(4, y, 5, y + 11, accent(y * 4));
                ctx.drawTextWithShadow(tr, s, 8, y + 2, 0xFFFFFFFF);
                y += 12;
            }
        }

        // Module list
        if (moduleList.enabled) {
            List<Module> active = new ArrayList<>();
            for (Module m : MODULES) if (m.enabled) active.add(m);
            active.sort(Comparator.comparingInt((Module m) -> tr.getWidth(m.name)).reversed());
            int y = 4, i = 0;
            for (Module m : active) {
                int w = tr.getWidth(m.name);
                int x = sw - w - 8;
                ctx.fill(x - 2, y, sw, y + 11, 0x80000000);
                ctx.fill(sw - 2, y, sw, y + 11, accent(i * 25));
                ctx.drawTextWithShadow(tr, m.name, x, y + 2, accent(i * 25));
                y += 11; i++;
            }
        }

        // Target HUD: информация о существе, на которое вы смотрите
        if (targetHud.enabled) {
            Entity t = mc.targetedEntity;
            if (t instanceof LivingEntity le && le.isAlive()) {
                lastTarget = le;
                lastSeen = System.currentTimeMillis();
            }
            if (lastTarget != null && lastTarget.isAlive() && System.currentTimeMillis() - lastSeen < 2000) {
                float hp = lastTarget.getHealth();
                float max = Math.max(1f, lastTarget.getMaxHealth());
                animHealth += (hp - animHealth) * 0.1f;

                int w = 130, h = 38;
                int x = sw / 2 + 30, y = sh / 2 + 20;
                ctx.fill(x, y, x + w, y + h, 0x90000000);
                ctx.fill(x, y, x + w, y + 1, accent(0));
                ctx.drawTextWithShadow(tr, lastTarget.getName().getString(), x + 5, y + 5, 0xFFFFFFFF);

                int barX = x + 5, barY = y + 17, barW = w - 10;
                ctx.fill(barX, barY, barX + barW, barY + 5, 0xFF303030);
                int fill = (int) (barW * MathHelper.clamp(animHealth / max, 0f, 1f));
                ctx.fill(barX, barY, barX + fill, barY + 5, accent(40));

                String hpText = String.format("HP %.1f / %.1f   Armor %d", hp, max, lastTarget.getArmor());
                ctx.drawTextWithShadow(tr, hpText, x + 5, y + 26, 0xFFCCCCCC);
            }
        }

        // Armor HUD: броня над хотбаром
        if (armorHud.enabled) {
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            int x = sw / 2 - 36, y = sh - 76;
            ctx.fill(x - 3, y - 2, x + 4 * 18 + 1, y + 19, 0x70000000);
            ctx.fill(x - 3, y + 18, x + 4 * 18 + 1, y + 19, accent(60));
            for (EquipmentSlot s : slots) {
                ItemStack st = mc.player.getEquippedStack(s);
                if (!st.isEmpty()) {
                    ctx.drawItem(st, x, y);
                    ctx.drawStackOverlay(tr, st, x, y);
                }
                x += 18;
            }
        }

        // Keystrokes: WASD и пробел (только показ нажатых клавиш)
        if (keystrokes.enabled) {
            int bx = 8, by = sh - 84;
            keyBox(ctx, tr, bx + 21, by, 18, "W", mc.options.forwardKey.isPressed());
            keyBox(ctx, tr, bx, by + 20, 18, "A", mc.options.leftKey.isPressed());
            keyBox(ctx, tr, bx + 21, by + 20, 18, "S", mc.options.backKey.isPressed());
            keyBox(ctx, tr, bx + 42, by + 20, 18, "D", mc.options.rightKey.isPressed());
            keyBox(ctx, tr, bx, by + 40, 60, "SPACE", mc.options.jumpKey.isPressed());
        }

        // Crit Indicator: подсказка, что сейчас удар будет критическим
        if (critIndicator.enabled) {
            if (isCritState(mc.player)) {
                String s = "CRIT";
                ctx.drawTextWithShadow(tr, s, sw / 2 - tr.getWidth(s) / 2, sh / 2 + 12, accent(0));
            }
            long since = System.currentTimeMillis() - critFlash;
            if (since < 600) {
                String s = "CRIT!";
                int rise = (int) (since / 30);
                ctx.drawTextWithShadow(tr, s, sw / 2 - tr.getWidth(s) / 2, sh / 2 - 26 - rise, 0xFFFFD34D);
            }
        }
    }
}
