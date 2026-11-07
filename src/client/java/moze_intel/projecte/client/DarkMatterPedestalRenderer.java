package moze_intel.projecte.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import moze_intel.projecte.DarkMatterPedestalBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

final class DarkMatterPedestalRenderer implements
        BlockEntityRenderer<DarkMatterPedestalBlockEntity, DarkMatterPedestalRenderer.State> {
    private final ItemModelResolver items;

    DarkMatterPedestalRenderer(BlockEntityRendererProvider.Context context) {
        items = context.itemModelResolver();
    }

    static final class State extends BlockEntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        float rotation;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DarkMatterPedestalBlockEntity pedestal, State state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(pedestal, state, partialTick, cameraPos, overlay);
        var level = pedestal.getLevel();
        items.updateForTopItem(state.item, pedestal.item(), ItemDisplayContext.FIXED, level, null,
                pedestal.getBlockPos().hashCode());
        state.rotation = level != null && pedestal.getBlockState().getValue(BlockStateProperties.LIT)
                ? ((level.getGameTime() % 120) + partialTick) * 3 : 0;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector nodes, CameraRenderState camera) {
        if (state.item.isEmpty()) return;
        pose.pushPose();
        pose.translate(0.5, 0.9, 0.5);
        pose.rotateDegrees(Axis.YP, state.rotation);
        pose.scale(0.55F, 0.55F, 0.55F);
        state.item.submit(pose, nodes, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
}
