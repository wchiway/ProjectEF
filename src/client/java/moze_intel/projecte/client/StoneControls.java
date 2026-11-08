package moze_intel.projecte.client;

import moze_intel.projecte.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;

/** Ordinary Minecraft key mappings: defaults are suggestions, never hard-coded input checks. */
public final class StoneControls {
 private StoneControls(){}
 public static void register(){
  var category=KeyMapping.Category.register(PECore.id("stone"));
  var charge=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.projecte.stone_charge",InputConstants.KEY_V,category));
  var lower=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.projecte.stone_lower",InputConstants.UNKNOWN.getValue(),category));
  var fire=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.projecte.stone_fire",InputConstants.KEY_R,category));
  var craft=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.projecte.stone_craft",InputConstants.KEY_C,category));
  var mode=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.projecte.stone_mode",InputConstants.KEY_G,category));
  // Options are loaded after mod client initializers. Repair the invalid sentinel saved by 1.2.2.
  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(client->{
   var key=KeyMappingHelper.getBoundKeyOf(lower);
   if(key.getType()==InputConstants.Type.KEYBOARD&&key.getValue()<0){
    lower.setKey(InputConstants.UNKNOWN);KeyMapping.resetMapping();
   }
  });
  ClientPreAttackCallback.EVENT.register((client, player, clicks) -> {
   if (!(player.getMainHandItem().getItem() instanceof ClassicRingItem ring) || !ring.supportsControl(5)
           || !player.mayBuild() || player.isSpectator()
           || !ClientPlayNetworking.canSend(PEPackets.StoneAction.TYPE)) return false;
   if (clicks > 0) send(client, 5);
   return true;
  });
  ClientTickEvents.END_CLIENT_TICK.register(client->{
   while(charge.consumeClick())send(client,client.player!=null&&client.player.isShiftKeyDown()?1:0);
   while(lower.consumeClick())send(client,1);
   while(fire.consumeClick())send(client,2);
   while(craft.consumeClick())send(client,3);
   while(mode.consumeClick())send(client,4);
  });
 }
 private static void send(Minecraft client,int command){
  if(client.player==null||client.gui.screen()!=null||!ClientPlayNetworking.canSend(PEPackets.StoneAction.TYPE))return;
  boolean offhand = !supportsControls(client.player.getMainHandItem().getItem(), command);
  if (offhand && !supportsControls(client.player.getOffhandItem().getItem(), command)) {
      if (!GemArmorEffects.supportsControl(client.player, command)) return;
      offhand = false;
  }
  ClientPlayNetworking.send(new PEPackets.StoneAction(command,offhand));
 }
 private static boolean supportsControls(net.minecraft.world.item.Item item, int command) {
  return item instanceof PhilosophersStoneItem && command < 5
          || item instanceof VolcaniteAmuletItem && command <= 2
          || item instanceof EvertideAmuletItem && command <= 3
          || item instanceof MercurialEyeItem eye && eye.supportsControl(command)
          || item instanceof ClassicRingItem ring && ring.supportsControl(command);
 }
}
