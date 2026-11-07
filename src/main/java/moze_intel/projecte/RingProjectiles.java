package moze_intel.projecte;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class RingProjectiles {
    private static final int LIFETIME = 32;
    private static final List<Shot> SHOTS = new ArrayList<>();

    static {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SHOTS.removeIf(shot -> shot.level.getServer() == server));
    }

    private RingProjectiles() { }

    static void snowball(ServerLevel level, ServerPlayer owner) {
        var shot = new Snowball(level, owner, new ItemStack(Items.SNOWBALL));
        shot.shootFromRotation(owner, owner.getXRot(), owner.getYRot(), 0, 1.5F, 0);
        level.addFreshEntity(shot);
    }

    static void launch(ServerLevel level, ServerPlayer owner, ItemStack ring, boolean lightning, boolean glass) {
        SHOTS.add(new Shot(level, owner, ring, lightning, glass));
    }

    static void tick(MinecraftServer server) {
        SHOTS.removeIf(shot -> shot.level.getServer() == server && !shot.advance());
    }

    private static final class Shot {
        private final ServerLevel level;
        private final ServerPlayer owner;
        private final ItemStack ring;
        private final boolean lightning;
        private final boolean glass;
        private final Vec3 velocity;
        private Vec3 pos;
        private int age;

        private Shot(ServerLevel level, ServerPlayer owner, ItemStack ring, boolean lightning, boolean glass) {
            this.level = level;
            this.owner = owner;
            this.ring = ring;
            this.lightning = lightning;
            this.glass = glass;
            velocity = owner.getLookAngle().scale(1.5);
            pos = owner.getEyePosition();
        }

        private boolean advance() {
            Vec3 next = pos.add(velocity);
            if (owner.level() != level || owner.hasDisconnected() || !ClassicRingItem.canUse(owner)
                    || !level.isLoaded(BlockPos.containing(next))) return false;
            var blockHit = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            boolean hitBlock = blockHit.getType() == HitResult.Type.BLOCK;
            Vec3 end = hitBlock ? blockHit.getLocation() : next;
            var mobHit = ProjectileUtil.getEntityHitResult(level, owner, pos, end, new AABB(pos, end).inflate(1),
                    entity -> entity instanceof LivingEntity && entity.isAlive()
                            && (!(entity instanceof Player other) || owner.canHarmPlayer(other)), 0.2F);
            if (mobHit != null) {
                if (lightning) strike(BlockPos.containing(mobHit.getLocation()));
                else if (ClassicRingItem.canChange(level, owner, ring, mobHit.getEntity().blockPosition())) {
                    mobHit.getEntity().igniteForSeconds(5);
                    burst(mobHit.getEntity().blockPosition());
                }
                return false;
            }
            if (hitBlock) {
                if (lightning) strike(blockHit.getBlockPos().relative(blockHit.getDirection()));
                else if (glass && level.getBlockState(blockHit.getBlockPos()).is(Blocks.SAND)) {
                    glassify(blockHit.getBlockPos());
                } else {
                    ClassicRingItem.light(level, owner, ring, blockHit.getBlockPos());
                    burst(blockHit.getBlockPos().relative(blockHit.getDirection()));
                }
                return false;
            }
            for (int step = 1; step <= 3; step++) {
                Vec3 point = pos.lerp(end, step / 3.0);
                level.sendParticles(lightning ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.FLAME,
                        point.x, point.y, point.z, 1, 0.02, 0.02, 0.02, 0);
            }
            pos = next;
            return ++age < LIFETIME;
        }

        private void burst(BlockPos center) {
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
                ClassicRingItem.light(level, owner, ring, pos);
            }
        }

        private void glassify(BlockPos center) {
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
                if (ClassicRingItem.canChange(level, owner, ring, pos) && level.getBlockState(pos).is(Blocks.SAND)) {
                    level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState());
                }
            }
        }

        private void strike(BlockPos pos) {
            if (!ClassicRingItem.canChange(level, owner, ring, pos)) return;
            var bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.snapTo(Vec3.atBottomCenterOf(pos));
                bolt.setCause(owner);
                level.addFreshEntity(bolt);
            }
        }
    }
}
