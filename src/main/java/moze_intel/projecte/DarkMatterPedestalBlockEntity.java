package moze_intel.projecte;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class DarkMatterPedestalBlockEntity extends BlockEntity {
    private static final int HARVEST_INTERVAL = 10;
    private ItemStack item = ItemStack.EMPTY;
    private UUID owner;

    DarkMatterPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(PERegistries.DARK_MATTER_PEDESTAL_ENTITY, pos, state);
    }

    static boolean supports(ItemStack stack) {
        return PedestalPowers.supports(stack);
    }

    public ItemStack item() {
        return item;
    }

    void insert(ItemStack stack, UUID player) {
        item = stack;
        owner = player;
        syncItem();
    }

    ItemStack removeItem() {
        ItemStack stored = item;
        item = ItemStack.EMPTY;
        owner = null;
        syncItem();
        return stored;
    }

    static void tick(Level level, BlockPos pos, BlockState state, DarkMatterPedestalBlockEntity pedestal) {
        if (!(level instanceof ServerLevel server) || !state.getValue(DarkMatterPedestalBlock.ACTIVE)
                || level.getGameTime() % HARVEST_INTERVAL != Math.floorMod(pos.hashCode(), HARVEST_INTERVAL)
                || !supports(pedestal.item)) return;
        FakePlayer actor = pedestal.owner == null ? FakePlayer.get(server)
                : FakePlayer.get(server, new GameProfile(pedestal.owner, "[ProjectEF Pedestal]"));
        PedestalPowers.apply(server, pos, actor, pedestal.item);
    }

    private void syncItem() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel server && !item.isEmpty()) {
            ItemStack stored = item;
            item = ItemStack.EMPTY;
            owner = null;
            Containers.dropItemStack(server, pos.getX(), pos.getY(), pos.getZ(), stored);
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!item.isEmpty()) output.store("item", ItemStack.CODEC, item);
        if (owner != null) output.store("owner", UUIDUtil.CODEC, owner);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        item = input.read("item", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
