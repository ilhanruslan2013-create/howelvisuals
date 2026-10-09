package howel.visuals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.random.Random;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HowelVisualsClient implements ClientModInitializer {

    /** Один визуальный модуль: имя, состояние, клавиша переключения. */
    static class Module {
        final String name;
        boolean enabled = true;
        final KeyBinding key;
        Module(String name, int defaultKey) {
            this.name = name;
            this.key = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.howelvisuals." + name.toLowerCase().replace(' ', '_'),
                    InputUtil.Type.KEYSYM, defaultKey, "category.howelvisuals"));
        }
    }

    static final List<Module> MODULES = new ArrayList<>();
    static Module watermark, moduleList, info, hitFx;
    static boolean hudVisible = true;
    static KeyBinding masterKey;
    static final Random RNG = Random.create();

    @Override
    public void onInitializeClient() {
        masterKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.howelvisuals.master", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.howelvisuals"));

        MODULES.add(watermark  = new Module("Watermark",   GLFW.GLFW_KEY_UNKNOWN));
        MODULES.add(moduleList = new Module("Module List", GLFW.GLFW_KEY_UNKNOWN));
        MODULES.add(info       = new Module("Info",        GLFW.GLFW_KEY_UNKNOWN));
        MODULES.add(hitFx      = new Module("Hit Effects", GLFW.GLFW_KEY_UNKNOWN));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (masterKey.wasPressed()) hudVisible = !hudVisible;
            for (Module m : MODULES) while (m.key.wasPressed()) m.enabled = !m.enabled;
        });

        HudRenderCallback.EVENT.register(HowelVisualsClient::render);

        // Частицы при ударе по сущности (только визуально, на стороне клиента)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient && hitFx.enabled) {
                for (int i = 0; i < 14; i++) {
                    world.addParticle(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.CRIT,
                        entity.getX(), entity.getBodyY(0.5), entity.getZ(),
                        (RNG.nextDouble() - 0.5) * 0.5, RNG.nextDouble() * 0.4, (RNG.nextDouble() - 0.5) * 0.5);
                }
            }
            return ActionResult.PASS;
        });
    }

    static int rainbow(int offset) {
        float hue = ((System.currentTimeMillis() / 20 + offset) % 360) / 360f;
        return Color.HSBtoRGB(hue, 0.65f, 1f) | 0xFF000000;
    }

    static void render(DrawContext ctx, net.minecraft.client.render.RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!hudVisible || mc.player == null || mc.options.hudHidden) return;
        var tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth();

        // Водяной знак с градиентными буквами
        if (watermark.enabled) {
            String text = "HowelVisuals";
            int w = tr.getWidth(text) + 10;
            ctx.fill(4, 4, 4 + w, 18, 0x90000000);
            ctx.fill(4, 4, 4 + w, 5, rainbow(0));
            int x = 9;
            for (int i = 0; i < text.length(); i++) {
                String ch = String.valueOf(text.charAt(i));
                ctx.drawTextWithShadow(tr, ch, x, 8, rainbow(i * 18));
                x += tr.getWidth(ch);
            }
        }

        // Инфо-панель: FPS, координаты, направление
        if (info.enabled) {
            String line1 = "FPS " + mc.getCurrentFps();
            String line2 = String.format("XYZ %.1f %.1f %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
            String line3 = "Facing " + mc.player.getHorizontalFacing().asString();
            int y = 24;
            for (String s : new String[]{line1, line2, line3}) {
                int w = tr.getWidth(s) + 8;
                ctx.fill(4, y, 4 + w, y + 11, 0x80000000);
                ctx.fill(4, y, 5, y + 11, rainbow(y * 4));
                ctx.drawTextWithShadow(tr, s, 8, y + 2, 0xFFFFFFFF);
                y += 12;
            }
        }

        // Список модулей справа, отсортирован по ширине
        if (moduleList.enabled) {
            List<Module> active = new ArrayList<>();
            for (Module m : MODULES) if (m.enabled) active.add(m);
            active.sort(Comparator.comparingInt((Module m) -> tr.getWidth(m.name)).reversed());
            int y = 4, i = 0;
            for (Module m : active) {
                int w = tr.getWidth(m.name);
                int x = sw - w - 8;
                ctx.fill(x - 2, y, sw, y + 11, 0x80000000);
                ctx.fill(sw - 2, y, sw, y + 11, rainbow(i * 25));
                ctx.drawTextWithShadow(tr, m.name, x, y + 2, rainbow(i * 25));
                y += 11; i++;
            }
        }
    }
}
