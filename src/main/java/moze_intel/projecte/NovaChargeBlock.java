package moze_intel.projecte;

import java.util.ArrayList;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.gamerules.GameRules;

public final class NovaChargeBlock extends TntBlock {
    private final int radius;

    NovaChargeBlock(Properties properties, int radius) {
        super(properties);
        this.radius = radius;
    }

    @Override
    public void wasExploded(ServerLevel level, BlockPos pos, Explosion blast) {
        if (!level.getGameRules().get(GameRules.TNT_EXPLODES)) return;
        var charge = new PrimedTnt(level, pos.getX() + .5, pos.getY(), pos.getZ() + .5, blast.getIndirectSourceEntity());
        charge.setBlockState(defaultBlockState());
        charge.setFuse(PrimedTnt.getRandomShortFuse(charge.getFuse(), level.getRandom()));
        level.addFreshEntity(charge);
    }

    public static boolean detonate(PrimedTnt charge) {
        if (!(charge.getBlockState().getBlock() instanceof NovaChargeBlock nova)
                || !(charge.level() instanceof ServerLevel level)) return false;
        if (!level.getGameRules().get(GameRules.TNT_EXPLODES)) return true;
        Player actor = charge.getOwner() instanceof Player player ? player : FakePlayer.get(level);
        BlockPos center = charge.blockPosition();
        int r = nova.radius;
        var drops = new ArrayList<ItemStack>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            if (pos.distSqr(center) > r * r || !ClassicRingItem.canChange(level, actor, ItemStack.EMPTY, pos)) continue;
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof NovaChargeBlock other) {
                if (level.removeBlock(pos, false)) {
                    var chained = new PrimedTnt(level, pos.getX() + .5, pos.getY(), pos.getZ() + .5, actor);
                    chained.setBlockState(other.defaultBlockState());
                    chained.setFuse(PrimedTnt.getRandomShortFuse(chained.getFuse(), level.getRandom()));
                    level.addFreshEntity(chained);
                }
            } else {
                MiningPowerItem.collect(level, pos, actor, drops);
            }
        }
        level.explode(charge, charge.getX(), charge.getY(), charge.getZ(), r, Level.ExplosionInteraction.NONE);
        LootBallItem.drop(level, charge.position(), drops);
        return true;
    }
}
