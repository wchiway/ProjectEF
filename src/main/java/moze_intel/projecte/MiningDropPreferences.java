package moze_intel.projecte;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

final class MiningDropPreferences extends SavedData {
    private static final Codec<MiningDropPreferences> CODEC = UUIDUtil.CODEC.listOf()
            .xmap(MiningDropPreferences::new, data -> List.copyOf(data.lootBallOptIns));
    // The previous save key held opt-outs. Reusing it would turn those players into opt-ins.
    private static final SavedDataType<MiningDropPreferences> TYPE = new SavedDataType<>(
            PECore.id("loot_ball_opt_ins"), () -> new MiningDropPreferences(List.of()), CODEC, null);

    private final Set<UUID> lootBallOptIns;

    private MiningDropPreferences(List<UUID> lootBallOptIns) {
        this.lootBallOptIns = new HashSet<>(lootBallOptIns);
    }

    private static MiningDropPreferences forPlayer(ServerPlayer player) {
        return player.level().getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    static boolean usesLootBalls(ServerPlayer player) {
        return forPlayer(player).lootBallOptIns.contains(player.getUUID());
    }

    static void registerCommand() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                dispatcher.register(Commands.literal("projecte")
                        .then(Commands.literal("lootballs")
                                .executes(command -> report(command.getSource(),
                                        usesLootBalls(command.getSource().getPlayerOrException())))
                                .then(Commands.literal("off").executes(command -> set(command.getSource(), false)))
                                .then(Commands.literal("on").executes(command -> set(command.getSource(), true))))));
    }

    private static int set(CommandSourceStack source, boolean enabled) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MiningDropPreferences data = forPlayer(player);
        boolean changed = enabled ? data.lootBallOptIns.add(player.getUUID()) : data.lootBallOptIns.remove(player.getUUID());
        if (changed) data.setDirty();
        return report(source, enabled);
    }

    private static int report(CommandSourceStack source, boolean enabled) {
        source.sendSuccess(() -> Component.literal(enabled
                ? "Area mining drops Loot Balls."
                : "Area mining drops regular items."), false);
        return 1;
    }
}
