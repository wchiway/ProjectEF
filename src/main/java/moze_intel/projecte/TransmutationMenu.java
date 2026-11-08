package moze_intel.projecte;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Real Minecraft slots provide carrying, drag distribution and shift-click synchronization. */
public final class TransmutationMenu extends AbstractContainerMenu {
 public final boolean tablet;
 public final long position;
 private final Player owner;
 private final SimpleContainer input=new SimpleContainer(1);
 private final SimpleContainer forget=new SimpleContainer(1);
 private ItemStack lastForget=ItemStack.EMPTY;
 private ItemStack dischargedStar=ItemStack.EMPTY;
 public TransmutationMenu(int id,Inventory inventory){this(id,inventory,false,0);}
 public TransmutationMenu(int id,Inventory inventory,boolean tablet,long position){
  super(PERegistries.TRANSMUTATION_MENU,id);owner=inventory.player;this.tablet=tablet;this.position=position;
  addSlot(new Slot(input,0,107,97));
  addSlot(new Slot(forget,0,89,97));
  for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,col+row*9+9,35+col*18,117+row*18));
  for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,35+col*18,175));
 }
 @Override public boolean stillValid(Player player){return !(player instanceof ServerPlayer server)||PETransmutationState.access(server,tablet,position);}
 @Override public void broadcastChanges(){
  if(owner instanceof ServerPlayer server && stillValid(owner)) {
   ItemStack deposited=input.getItem(0);
   if(deposited!=dischargedStar) {
    dischargedStar=ItemStack.EMPTY;
    if(!deposited.isEmpty()) {
     var result=PETransmutationState.depositInput(server,deposited,deposited.getCount());
     // Leave the drained star in the input until it is taken back.
     if(result==PETransmutationState.DepositResult.DISCHARGED)dischargedStar=deposited;
     if(result!=PETransmutationState.DepositResult.NONE) {
      input.setChanged();
      PETransmutationState.sync(server,tablet,position);
     }
    }
   }
   ItemStack stack=forget.getItem(0);
   if(!ItemStack.matches(stack,lastForget)) {
    lastForget=stack.copy();
    if(!stack.isEmpty() && PETransmutationState.forgetStack(server,stack))PETransmutationState.sync(server,tablet,position);
   }
  }
  super.broadcastChanges();
 }
 @Override public ItemStack quickMoveStack(Player player,int index){
  if(index<0||index>=slots.size())return ItemStack.EMPTY;
  Slot slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
  ItemStack stack=slot.getItem(),copy=stack.copy();
  if(index<2){if(!moveItemStackTo(stack,2,slots.size(),true))return ItemStack.EMPTY;}
  else if(player instanceof ServerPlayer server && stillValid(player)){
   if(PETransmutationState.depositInput(server,stack,stack.getCount())==PETransmutationState.DepositResult.NONE)return ItemStack.EMPTY;
   PETransmutationState.sync(server,tablet,position);
  }else return ItemStack.EMPTY;
  if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();
  return copy;
 }
 @Override public void removed(Player player){super.removed(player);if(player instanceof ServerPlayer){clearContainer(player,input);clearContainer(player,forget);}}
}
