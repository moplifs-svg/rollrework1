package com.rollmod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side roll/dash charges and cooldowns. */
@Mod.EventBusSubscriber(modid = RollMod.MODID)
public class RollHandler {
    public static final int MAX_CHARGES = 2;
    /** Delay between two rolls: 20 ticks = 1 second. */
    public static final int ROLL_COOLDOWN_TICKS = 20;
    /** Time to regain one charge: 60 ticks = 3 seconds. */
    public static final int RECHARGE_TICKS = 60;

    /** Invincibility frames while rolling: 8 ticks = 0.4 seconds. */
    public static final int IFRAME_TICKS = 8;

    private static class State {
        int iframes = 0;
        int charges = MAX_CHARGES;
        int recharge = 0;
        int cooldown = 0;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static State get(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), k -> new State());
    }

    /** forward / left are the player's movement input (-1..1) when pressing the roll key. */
    public static void tryRoll(ServerPlayer player, float forward, float left) {
        if (!player.isAlive() || player.isSpectator() || player.isPassenger()
                || player.isFallFlying() || player.isSleeping()) return;

        State s = get(player);
        if (s.cooldown > 0 || s.charges <= 0) return;

        forward = Mth.clamp(forward, -1.0F, 1.0F);
        left = Mth.clamp(left, -1.0F, 1.0F);

        double yaw = Math.toRadians(player.getYRot());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double lx = Math.cos(yaw);
        double lz = Math.sin(yaw);

        double dx = fx * forward + lx * left;
        double dz = fz * forward + lz * left;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4D) { // no keys held: roll forward
            dx = fx;
            dz = fz;
        } else {
            dx /= len;
            dz /= len;
        }

        if (s.charges == MAX_CHARGES) {
            s.recharge = RECHARGE_TICKS;
        }
        s.charges--;
        s.cooldown = ROLL_COOLDOWN_TICKS;
        s.iframes = IFRAME_TICKS;

        Network.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new RollSyncPacket(player.getId(), (float) dx, (float) dz));
        sendStatus(player, s);

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    private static void sendStatus(ServerPlayer player, State s) {
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new RollStatusPacket(s.charges, s.recharge, s.cooldown));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        State s = STATES.get(player.getUUID());
        if (s == null) return;

        if (s.iframes > 0) s.iframes--;
        if (s.cooldown > 0) s.cooldown--;
        if (s.charges < MAX_CHARGES) {
            s.recharge--;
            if (s.recharge <= 0) {
                s.charges++;
                s.recharge = s.charges < MAX_CHARGES ? RECHARGE_TICKS : 0;
                sendStatus(player, s);
            }
        }
    }

    /** I-frames: attacks from mobs, players and projectiles do nothing while rolling. */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        State s = STATES.get(player.getUUID());
        if (s == null || s.iframes <= 0) return;

        var source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return; // /kill, void
        if (source.getEntity() == null && source.getDirectEntity() == null) return; // fall, fire, lava...
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendStatus(player, get(player));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }
}
