package howel.visuals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
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
 * HowelVisuals: только визуальные элементы (HUD, меню-хаб, TargetHUD, броня, клавиши,
 * подсказка крита, эффекты). Модули можно включать мышкой или назначить на каждый свою клавишу.
 * Ничего не влияет на геймплей и не совершает действий за игрока.
 */
public class HowelVisualsClient implements ClientModInitializer {

    static class Module {
        final String name;
        boolean enabled = true;
        int bind = GLFW.GLFW_KEY_UNKNOWN;
        boolean prevDown = false;
        float anim = 1f;
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
            // Назначенные клавиши включают и выключают визуальные модули
            if (client.currentScreen == null && client.getWindow() != null) {
                long handle = client.getWindow().getHandle();
                for (Module m : MODULES) {
                    if (m.bind == GLFW.GLFW_KEY_UNKNOWN) continue;
                    boolean down = InputUtil.isKeyPressed(handle, m.bind);
                    if (down && !m.prevDown) m.enabled = !m.enabled;
                    m.prevDown = down;
                }
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

    static String keyName(int code) {
        if (code == GLFW.GLFW_KEY_UNKNOWN) return "None";
        return InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText().getString();
    }

    // ---------- Меню-хаб (Right Shift) ----------

    public static class HowelGuiScreen extends Screen {
        static final int PW = 380, PH = 206;
        static final int COL_W = 176, ROW_H = 30, ROW_STEP = 34;

        private Module listening = null;

        public HowelGuiScreen() {
            super(Text.literal("HowelVisuals"));
        }

        @Override
        public boolean shouldPause() {
            return false;
        }

        private int px() { return (this.width - PW) / 2; }
        private int py() { return (this.height - PH) / 2; }

        private static boolean over(double mx, double my, int x, int y, int w, int h) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            int px = px(), py = py();

            // затемнение фона и панель
            ctx.fill(0, 0, this.width, this.height, 0x90000000);
            ctx.fill(px, py, px + PW, py + PH, 0xF0101018);
            ctx.fill(px, py, px + PW, py + 2, accent(0));

            // заголовок
            ctx.drawTextWithShadow(this.textRenderer, "Visuals", px + 14, py + 14, accent(0));
            ctx.drawTextWithShadow(this.textRenderer, "HowelVisuals", px + 14 + 52, py + 14, 0xFF6C6C80);

            // кнопка темы
            int tbx = px + PW - 118, tby = py + 9, tbw = 106, tbh = 18;
            boolean tHover = over(mouseX, mouseY, tbx, tby, tbw, tbh);
            ctx.fill(tbx, tby, tbx + tbw, tby + tbh, tHover ? 0xFF2A2A38 : 0xFF1C1C28);
            String tt = "Theme: " + THEME_NAMES[theme];
            ctx.drawTextWithShadow(this.textRenderer, tt, tbx + (tbw - this.textRenderer.getWidth(tt)) / 2, tby + 5, 0xFFFFFFFF);

            // карточки модулей
            for (int i = 0; i < MODULES.size(); i++) {
                Module m = MODULES.get(i);
                int rx = px + 12 + (i % 2) * (COL_W + 4);
                int ry = py + 40 + (i / 2) * ROW_STEP;

                boolean hover = over(mouseX, mouseY, rx, ry, COL_W, ROW_H);
                ctx.fill(rx, ry, rx + COL_W, ry + ROW_H, hover ? 0xFF222230 : 0xFF181822);

                // название
                ctx.drawTextWithShadow(this.textRenderer, m.name, rx + 8, ry + 5, 0xFFFFFFFF);

                // кнопка бинда
                String bindText = (listening == m) ? "Press a key..." : "Bind: [" + keyName(m.bind) + "]";
                boolean bHover = over(mouseX, mouseY, rx + 8, ry + 16, 110, 10);
                int bColor = (listening == m) ? 0xFFFFD34D : (bHover ? 0xFFFFFFFF : 0xFF8888A0);
                ctx.drawText(this.textRenderer, bindText, rx + 8, ry + 17, bColor, false);

                // переключатель
                m.anim += ((m.enabled ? 1f : 0f) - m.anim) * 0.25f;
                int sx = rx + COL_W - 8 - 28, sy = ry + 9;
                int bg = m.enabled ? accent(i * 20) : 0xFF3A3A48;
                ctx.fill(sx, sy, sx + 28, sy + 12, bg);
                int kx = sx + 1 + (int) (m.anim * 16);
                ctx.fill(kx, sy + 1, kx + 10, sy + 11, 0xFFFFFFFF);
            }

            // подсказка
            ctx.drawText(this.textRenderer,
                "Click a card to toggle  |  Click Bind, press a key  |  Del = clear  |  Esc = close",
                px + 12, py + PH - 16, 0xFF6C6C80, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int px = px(), py = py();

            // тема
            if (over(mouseX, mouseY, px + PW - 118, py + 9, 106, 18)) {
                theme = (theme + 1) % THEME_NAMES.length;
                listening = null;
                return true;
            }

            for (int i = 0; i < MODULES.size(); i++) {
                Module m = MODULES.get(i);
                int rx = px + 12 + (i % 2) * (COL_W + 4);
                int ry = py + 40 + (i / 2) * ROW_STEP;
                if (!over(mouseX, mouseY, rx, ry, COL_W, ROW_H)) continue;

                if (over(mouseX, mouseY, rx + 8, ry + 16, 110, 10)) {
                    listening = (listening == m) ? null : m;
                } else {
                    m.enabled = !m.enabled;
                    listening = null;
                }
                return true;
            }
            listening = null;
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (listening != null) {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    // отмена выбора
                } else if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                    listening.bind = GLFW.GLFW_KEY_UNKNOWN;
                } else {
                    listening.bind = keyCode;
                    listening.prevDown = true; // чтобы не переключилось сразу при закрытии меню
                }
                listening = null;
                return true;
            }
            if (guiKey.matchesKey(keyCode, scanCode)) {
                close();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
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
