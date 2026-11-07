package moze_intel.projecte;

import java.lang.reflect.Method;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.component.TypedEntityData;

public final class MachineLearningTests {
    private static Object account(ServerPlayer player)throws Exception{
        var state=player.level().getServer().overworld().getDataStorage().computeIfAbsent(PETransmutationState.TYPE);
        Method method=PETransmutationState.class.getDeclaredMethod("account",ServerPlayer.class);method.setAccessible(true);
        return method.invoke(state,player);
    }
    private static Object field(Object account,String name)throws Exception{
        var method=account.getClass().getDeclaredMethod(name);method.setAccessible(true);return method.invoke(account);
    }
    private static ServerPlayer player(GameTestHelper h){
        var player=h.makeMockServerPlayerInLevel();player.setUUID(UUID.randomUUID());
        player.getInventory().setItem(0,new ItemStack(BuiltInRegistries.ITEM.getValue(PECore.id("transmutation_tablet"))));
        player.containerMenu=new TransmutationMenu(1,player.getInventory(),true,0);
        return player;
    }
    @GameTest public void brokenAaMachinesLearnAnEmptyPurchasableBlock(GameTestHelper h)throws Exception{
        if(!BuiltInRegistries.ITEM.containsKey(Identifier.parse("actuallyadditions:lamp_controller"))){h.succeed();return;}
        int index=0;
        for(String path:List.of("lamp_controller","fermenting_barrel","canola_press","coal_generator")){
            String id="actuallyadditions:"+path;
            var item=BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
            var block=((BlockItem)item).getBlock();var pos=h.absolutePos(new BlockPos(1+index++,2,1));
            h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());
            var entity=h.getLevel().getBlockEntity(pos);
            var drops=Block.getDrops(block.defaultBlockState(),h.getLevel(),pos,entity);
            var dropped=drops.stream().filter(s->s.is(item)).findFirst().orElseThrow();
            h.assertTrue(dropped.has(DataComponents.BLOCK_ENTITY_DATA),"real broken AA machine carries the data missed by the former fresh-item test: "+path);
            // Verify operational state is never present in the learned/purchased item.
            var data=dropped.get(DataComponents.BLOCK_ENTITY_DATA);var tag=data.copyTagWithoutId();tag.putInt("energy",123456);
            dropped.set(DataComponents.BLOCK_ENTITY_DATA,TypedEntityData.of(data.type(),tag));
            var player=player(h);long unit=PEEmcOverrides.forPlayer(player).value(dropped);
            h.assertTrue(unit>0,"Automatic EMC values the real machine drop: "+path);
            player.getInventory().setItem(1,dropped);
            // Exercise the actual GUI input-slot path, not only a direct helper call.
            var menu=(TransmutationMenu)player.containerMenu;menu.getSlot(0).set(dropped);player.getInventory().setItem(1,ItemStack.EMPTY);
            menu.broadcastChanges();
            h.assertTrue(menu.getSlot(0).getItem().isEmpty(),"input-slot sale consumes the machine: "+path);
            var learned=account(player);
            h.assertTrue(((Long)field(learned,"balance"))==unit,"sale credits the full machine value: "+path);
            h.assertTrue(((List<?>)field(learned,"learned")).contains(id),"sold broken machine is learned: "+path);
            h.assertTrue(((List<?>)field(learned,"variants")).isEmpty(),"operational machine data is not saved as a purchasable variant: "+path);
            PETransmutationState.action(player,new PEPackets.Action(true,0,"buy",0,id,1));
            ItemStack bought=ItemStack.EMPTY;
            for(int slot=1;slot<36;slot++)if(player.getInventory().getItem(slot).is(item))bought=player.getInventory().getItem(slot);
            h.assertTrue(!bought.isEmpty()&&!bought.has(DataComponents.BLOCK_ENTITY_DATA),"learned machine buys back empty, without saved energy or fluids: "+path);
            h.assertTrue(((Long)field(account(player),"balance"))==0,"buy-back charges the sale's full EMC: "+path);
            System.out.println("MACHINE LEARNING PASS: "+path+" real drop -> input slot -> EMC credit -> learned -> empty buy-back");
        }
        h.succeed();
    }
    @GameTest public void storedInventoryContentsAreNotLearnedAsCopies(GameTestHelper h)throws Exception{
        var player=player(h);var stack=new ItemStack(Items.SHULKER_BOX);
        stack.set(DataComponents.CONTAINER,net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND,64))));
        var menu=(TransmutationMenu)player.containerMenu;menu.getSlot(0).set(stack);menu.broadcastChanges();
        h.assertTrue(((List<?>)field(account(player),"variants")).isEmpty(),"full portable inventories do not become exact learned variants");
        h.succeed();
    }
}
