package moze_intel.projecte;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.network.chat.Component;

/** Unbreakable matter equipment with explicit charge levels and bounded area actions. */
public final class MatterToolItem extends Item {
 public final String kind;public final boolean red;
 public MatterToolItem(Properties p,String kind,boolean red){super(equipment(p,kind,red));this.kind=kind;this.red=red;}
 private static Properties equipment(Properties p,String kind,boolean red){
  ToolMaterial m=new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL,10000,red?24:16,red?12:8,10,ItemTags.REPAIRS_NETHERITE_ARMOR);
  switch(kind){
   case "pick","hammer","morning_star"->p.pickaxe(m,1,-2.8f);
   case "axe","katar"->p.axe(m,4,-3);
   case "shovel"->p.shovel(m,1.5f,-3);
   case "hoe"->p.hoe(m,0,-1);
   case "sword"->p.sword(m,red?5:3,-2.4f);
   case "shears"->p.delayedComponent(DataComponents.TOOL, registries -> new net.minecraft.world.item.component.Tool(List.of(
    net.minecraft.world.item.component.Tool.Rule.minesAndDrops(registries.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK).getOrThrow(BlockTags.LEAVES),15),
    net.minecraft.world.item.component.Tool.Rule.minesAndDrops(net.minecraft.core.HolderSet.direct(Blocks.COBWEB.builtInRegistryHolder()),15),
    net.minecraft.world.item.component.Tool.Rule.overrideSpeed(registries.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK).getOrThrow(BlockTags.WOOL),5)),1,0,true));
  }
  return p.stacksTo(1).fireResistant().component(DataComponents.UNBREAKABLE,net.minecraft.util.Unit.INSTANCE);
 }
 public static int charge(ItemStack stack){return Math.clamp(stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getIntOr("MatterCharge",0),0,3);}
 private InteractionResult charge(Player player,InteractionHand hand){
  if(!player.level().isClientSide()){
   ItemStack stack=player.getItemInHand(hand);var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();int charge=(charge(stack)+1)%(red?4:3);data.putInt("MatterCharge",charge);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));player.sendSystemMessage(Component.literal("Charge: "+charge));
  }
  return InteractionResult.SUCCESS;
 }
 @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
  if(player.isShiftKeyDown())return charge(player,hand);
  if(kind.equals("sword")||kind.equals("katar")){
   if(level instanceof ServerLevel server){int c=charge(player.getItemInHand(hand));for(LivingEntity mob:server.getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(3+c),e->e instanceof Enemy&&e.isAlive()&&!player.isAlliedTo(e)))mob.hurtServer(server,server.damageSources().playerAttack(player),red?18:14);player.getCooldowns().addCooldown(player.getItemInHand(hand),20);}
   return InteractionResult.SUCCESS;
  }
  return InteractionResult.PASS;
 }
 @Override public InteractionResult useOn(UseOnContext c){
  Player player=c.getPlayer();if(player==null)return InteractionResult.PASS;
  if(player.isShiftKeyDown())return charge(player,c.getHand());
  int charge=charge(c.getItemInHand());if(charge==0&&!kind.equals("hoe")&&!kind.equals("shovel"))return InteractionResult.PASS;
  if(!(c.getLevel() instanceof ServerLevel level))return InteractionResult.SUCCESS;
  BlockPos origin=c.getClickedPos();Set<BlockPos> blocks=new LinkedHashSet<>();
  if(kind.equals("axe")||kind.equals("katar")){
   ArrayDeque<BlockPos> work=new ArrayDeque<>();Set<BlockPos> seen=new HashSet<>();work.add(origin);
   while(!work.isEmpty()&&blocks.size()<256){BlockPos p=work.removeFirst();if(!seen.add(p)||!level.isLoaded(p)||!level.getBlockState(p).is(BlockTags.LOGS)||p.distSqr(origin)>256)continue;blocks.add(p);for(Direction d:Direction.values())work.add(p.relative(d));}
  }else{
   int r=Math.min(charge,2);Direction.Axis axis=c.getClickedFace().getAxis();
   for(int a=-r;a<=r;a++)for(int b=-r;b<=r;b++)blocks.add(axis==Direction.Axis.X?origin.offset(0,a,b):axis==Direction.Axis.Y?origin.offset(a,0,b):origin.offset(a,b,0));
  }
  List<ItemStack> drops = new ArrayList<>();
  for(BlockPos p:blocks){
   if(!level.isLoaded(p)||!level.mayInteract(player,p)||!player.mayUseItemAt(p,c.getClickedFace(),c.getItemInHand()))continue;
   BlockState state=level.getBlockState(p);
   if(state.hasBlockEntity()||state.getDestroySpeed(level,p)<0||state.isAir())continue;
   if(kind.equals("hoe")){
    if((state.is(Blocks.DIRT)||state.is(Blocks.GRASS_BLOCK)||state.is(Blocks.DIRT_PATH))&&level.getBlockState(p.above()).isAir())level.setBlock(p,Blocks.FARMLAND.defaultBlockState(),3);
    else if(state.getBlock() instanceof CropBlock crop&&crop.isMaxAge(state)){Block.dropResources(state,level,p,null,player,c.getItemInHand());level.setBlock(p,crop.getStateForAge(0),3);}
    continue;
   }
   if(kind.equals("shovel")&&charge==0){
    if((state.is(Blocks.DIRT)||state.is(Blocks.GRASS_BLOCK)||state.is(Blocks.COARSE_DIRT))&&level.getBlockState(p.above()).isAir())level.setBlock(p,Blocks.DIRT_PATH.defaultBlockState(),3);
    continue;
   }
   boolean mine=switch(kind){case "pick","hammer"->state.is(BlockTags.MINEABLE_WITH_PICKAXE);case "morning_star"->state.is(BlockTags.MINEABLE_WITH_PICKAXE)||state.is(BlockTags.MINEABLE_WITH_SHOVEL);case "shovel"->state.is(BlockTags.MINEABLE_WITH_SHOVEL);case "axe","katar"->state.is(BlockTags.LOGS);case "shears"->state.is(BlockTags.LEAVES)||state.is(Blocks.COBWEB)||state.is(Blocks.VINE);default->false;};
   if (mine) {
    var loot = level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.BLOCK_DROPS)
            ? Block.getDrops(state, level, p, null, player, c.getItemInHand()) : List.<ItemStack>of();
    if (level.destroyBlock(p, false, player)) drops.addAll(loot);
   }
  }
  LootBallItem.drop(level, net.minecraft.world.phys.Vec3.atCenterOf(origin.relative(c.getClickedFace())), drops);
  player.getCooldowns().addCooldown(c.getItemInHand(),10);return InteractionResult.SUCCESS;
 }
 @Override public InteractionResult interactLivingEntity(ItemStack stack,Player player,LivingEntity target,InteractionHand hand){
  if(!kind.equals("shears")&&!kind.equals("katar"))return InteractionResult.PASS;
  if(!(target instanceof Shearable shearable)||!shearable.readyForShearing())return InteractionResult.PASS;
  if(player.level() instanceof ServerLevel level){
   shearable.shear(level,net.minecraft.sounds.SoundSource.PLAYERS,stack);
   int charge=charge(stack);
   if(charge>0)for(LivingEntity nearby:level.getEntitiesOfClass(LivingEntity.class,target.getBoundingBox().inflate(charge+1),e->e!=target&&e instanceof Shearable s&&s.readyForShearing()))((Shearable)nearby).shear(level,net.minecraft.sounds.SoundSource.PLAYERS,stack);
  }
  return InteractionResult.SUCCESS;
 }
 @Override public void appendHoverText(ItemStack stack,TooltipContext context,net.minecraft.world.item.component.TooltipDisplay display,java.util.function.Consumer<Component> lines,TooltipFlag flag){
  lines.accept(Component.literal("Charge: "+charge(stack)+" · Sneak + right click to cycle"));
  lines.accept(Component.literal("Charged right click: area action"));
 }
}
