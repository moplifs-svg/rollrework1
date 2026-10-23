package com.rollmod;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Client-only part of the roll mod: key (default C), dash movement, rolling model animation, HUD. */
public class RollClient {
    public static final KeyMapping ROLL_KEY = new KeyMapping(
            "key.roll.roll",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            "key.categories.roll");

    /**
     * Push speed per tick (blocks/tick): starts fast and eases out, so the roll ends softly
     * instead of stopping dead (also when landing from an air roll). Total is about 3 blocks.
     */
    private static final double[] DASH_SPEEDS = {0.70D, 0.60D, 0.50D, 0.40D, 0.30D, 0.20D, 0.08D};
    private static final int DASH_TICKS = DASH_SPEEDS.length;
    /** Length of the rolling animation in ticks. */
    private static final int ANIM_TICKS = DASH_TICKS;

    private static class Anim {
        final float dx;
        final float dz;
        int ticks = 0;
        boolean pushed = false;

        Anim(float dx, float dz) {
            this.dx = dx;
            this.dz = dz;
        }
    }

    private static final Map<Integer, Anim> ANIMS = new HashMap<>();
    private static final ItemStack ICON = new ItemStack(Items.LEATHER_BOOTS);

    private static int dashLeft = 0;
    private static double dashDx = 0;
    private static double dashDz = 0;

    private static int charges = RollHandler.MAX_CHARGES;
    private static int recharge = 0;

    public static void onRoll(int entityId, float dx, float dz) {
        ANIMS.put(entityId, new Anim(dx, dz));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == entityId) {
            dashLeft = DASH_TICKS;
            dashDx = dx;
            dashDz = dz;
        }
    }

    public static void onStatus(int newCharges, int newRecharge, int newCooldown) {
        charges = newCharges;
        recharge = newRecharge;
    }

    @Mod.EventBusSubscriber(modid = RollMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(ROLL_KEY);
        }
    }

    @Mod.EventBusSubscriber(modid = RollMod.MODID, value = Dist.CLIENT)
    public static class Events {

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.START) return;
            Minecraft mc = Minecraft.getInstance();

            Iterator<Map.Entry<Integer, Anim>> it = ANIMS.entrySet().iterator();
            while (it.hasNext()) {
                Anim a = it.next().getValue();
                if (++a.ticks >= ANIM_TICKS) it.remove();
            }
            if (recharge > 0) recharge--;

            while (ROLL_KEY.consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    Network.CHANNEL.sendToServer(new RollPacket(
                            mc.player.input.forwardImpulse, mc.player.input.leftImpulse));
                }
            }
        }

        /** Pushes the local player along the roll direction (runs before the player moves). */
        @SubscribeEvent
        public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.START) return;
            if (dashLeft <= 0) return;
            if (event.player != Minecraft.getInstance().player) return;

            if (!event.player.isAlive() || event.player.isPassenger()) {
                dashLeft = 0;
                return;
            }
            double speed = DASH_SPEEDS[DASH_TICKS - dashLeft];
            Vec3 m = event.player.getDeltaMovement();
            event.player.setDeltaMovement(dashDx * speed, m.y, dashDz * speed);
            dashLeft--;
        }

        /** Third person / other players: the model does a full somersault in the roll direction. */
        @SubscribeEvent
        public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
            Anim a = ANIMS.get(event.getEntity().getId());
            if (a == null) return;

            float progress = Mth.clamp((a.ticks + event.getPartialTick()) / ANIM_TICKS, 0.0F, 1.0F);
            float centerY = event.getEntity().getBbHeight() / 2.0F;
            PoseStack stack = event.getPoseStack();
            stack.pushPose();
            a.pushed = true;
            stack.translate(0.0D, centerY, 0.0D);
            // axis = up x direction, so the head leads in the roll direction
            stack.mulPose(Axis.of(new Vector3f(a.dz, 0.0F, -a.dx)).rotationDegrees(360.0F * progress));
            stack.translate(0.0D, -centerY, 0.0D);
        }

        @SubscribeEvent
        public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
            Anim a = ANIMS.get(event.getEntity().getId());
            if (a != null && a.pushed) {
                event.getPoseStack().popPose();
                a.pushed = false;
            }
        }

        /** Boots icon + charges (and seconds until the next charge) under the parry cooldown. */
        @SubscribeEvent
        public static void onRenderGui(RenderGuiEvent.Post event) {
            if (charges >= RollHandler.MAX_CHARGES) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui) return;

            GuiGraphics g = event.getGuiGraphics();
            int cx = event.getWindow().getGuiScaledWidth() / 2;
            int y = event.getWindow().getGuiScaledHeight() / 2 + 34;
            String text = charges + "/" + RollHandler.MAX_CHARGES
                    + (recharge > 0 ? "  " + ((recharge + 19) / 20) + "s" : "");

            int total = 16 + 3 + mc.font.width(text);
            int x = cx - total / 2;
            g.renderItem(ICON, x, y);
            g.drawString(mc.font, text, x + 19, y + 4, 0x55FFFF, true);
        }
    }
}
