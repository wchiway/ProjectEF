package moze_intel.projecte;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class InterdictionTorchBlock extends TorchBlock {
    InterdictionTorchBlock(Properties properties) {
        super(ParticleTypes.FLAME, properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!level.isClientSide()) level.scheduleTick(pos, this, 5);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Vec3 center = Vec3.atCenterOf(pos);
        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(5),
                entity -> entity instanceof Enemy && entity.isAlive())) {
            Vec3 offset = mob.position().subtract(center);
            if (offset.lengthSqr() <= 25) {
                Vec3 push = offset.normalize().scale(.45);
                mob.push(push.x, .08, push.z);
            }
        }
        level.scheduleTick(pos, this, 5);
    }
}
