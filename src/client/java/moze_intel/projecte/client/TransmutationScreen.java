package moze_intel.projecte.client;

import java.util.*;
import com.mojang.blaze3d.platform.InputConstants;
import moze_intel.projecte.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;

public class TransmutationScreen extends AbstractContainerScreen<TransmutationMenu> {
    private static final Identifier TEXTURE=PECore.id("textures/gui/transmute.png");
    private static final int[][] FAVORITES={{43,23},{34,41},{52,41},{16,50},{70,50},{34,59},{52,59},{43,77}};
    private static final int[][] LEARNED={{158,9},{176,13},{193,30},{199,50},{193,70},{176,87},{158,91},{140,87},{123,70},{116,50},{123,30},{140,13},{158,31},{177,50},{158,69},{139,50}};
    private static String rememberedSearch="";
    private static int rememberedPage,rememberedFavoritePage;
    private boolean tablet,sweeping;
    private long position,balance;
    private int knowledgeRevision,page,favoritePage,left,top;
    private List<String> learned=List.of(),favorites=List.of(),filtered=List.of();
    private Map<String,Long> prices=Map.of();
    private List<ItemStack> variants=List.of();
    private final Set<Integer> sweptSlots=new HashSet<>();
    private EditBox search;
    private String dragKey,selectedKey;
    private double dragStartX,dragStartY;

    public TransmutationScreen(TransmutationMenu menu,Inventory inventory,Component title){
        super(menu,inventory,title,228,196);page=rememberedPage;favoritePage=rememberedFavoritePage;
    }
    public void update(PEPackets.Snapshot snapshot){
        if(knowledgeRevision!=snapshot.knowledgeRevision()){dragKey=null;selectedKey=null;}
        tablet=snapshot.tablet();position=snapshot.position();balance=snapshot.balance();
        knowledgeRevision=snapshot.knowledgeRevision();learned=snapshot.learned();prices=snapshot.prices();variants=snapshot.variants();
        favorites=snapshot.favorites().stream().filter(this::known).toList();refreshList();
    }
    private boolean known(String key){
        if(!key.startsWith("variant:"))return learned.contains(key);
        try{int i=Integer.parseInt(key.substring(8));return i>=0&&i<variants.size();}catch(NumberFormatException invalid){return false;}
    }
    private ItemStack exemplar(String key){return key.startsWith("variant:")?variants.get(Integer.parseInt(key.substring(8))):new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(key)));}
    private String label(ItemStack stack){
        StringBuilder text=new StringBuilder(stack.getHoverName().getString());
        var ench=stack.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        if(stack.is(Items.ENCHANTED_BOOK))ench=stack.getOrDefault(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS,net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        for(var e:ench.entrySet())text.append(" · ").append(e.getKey().value().description().getString()).append(" ").append(e.getIntValue());
        return text.toString();
    }
    private long price(ItemStack stack){String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();return PETransmutationState.valueWithUpgrades(stack,PEEmcBlacklist.contains(id)?0:prices.getOrDefault(id,PETransmutationState.value(id)));}
    @Override protected void init(){
        super.init();left=leftPos;top=topPos;
        search=addRenderableWidget(new EditBox(font,left+83,top+8,55,12,Component.literal("Search learned items")));
        search.setValue(rememberedSearch);search.setResponder(value->{rememberedSearch=value;page=0;refreshList();});refreshList();
        TransmutationInput.setSearchFocused(this,search,true);
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event){
        if(TransmutationInput.searchOwnsKey(search!=null&&search.isFocused(),event.key())){search.keyPressed(event);return true;}
        return super.keyPressed(event);
    }
    private void refreshList(){
        String query=search==null?rememberedSearch:search.getValue();query=query.toLowerCase(Locale.ROOT);
        List<String> matches=new ArrayList<>();
        for(String id:learned)if(label(exemplar(id)).toLowerCase(Locale.ROOT).contains(query))matches.add(id);
        for(int i=0;i<variants.size();i++)if(label(variants.get(i)).toLowerCase(Locale.ROOT).contains(query))matches.add("variant:"+i);
        filtered=matches;page=Math.clamp(page,0,Math.max(0,(filtered.size()-1)/LEARNED.length));
        favoritePage=Math.clamp(favoritePage,0,Math.max(0,(favorites.size()-1)/FAVORITES.length));
        rememberedPage=page;rememberedFavoritePage=favoritePage;
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int x,int y){}
    @Override public void extractContents(GuiGraphicsExtractor g,int mouseX,int mouseY,float partial){
        g.blit(RenderPipelines.GUI_TEXTURED,TEXTURE,left,top,0,0,228,196,256,256);
        super.extractContents(g,mouseX,mouseY,partial);
        g.text(font,"Favorites",left+6,top+8,0xFF404040,false);
        g.text(font,"EMC: "+balance,left+6,top+100,0xFF404040,false);
        g.text(font,"Learned "+(page+1)+"/"+Math.max(1,(filtered.size()+LEARNED.length-1)/LEARNED.length),left+139,top+108,0xFF404040,false);
        if(page>0)g.text(font,"<",left+116,top+108,0xFF404040,false);
        if((page+1)*LEARNED.length<filtered.size())g.text(font,">",left+215,top+108,0xFF404040,false);
        if(favoritePage>0)g.text(font,"<",left+8,top+84,0xFF404040,false);
        if((favoritePage+1)*FAVORITES.length<favorites.size())g.text(font,">",left+84,top+84,0xFF404040,false);
        draw(g,mouseX,mouseY,filtered,page,LEARNED,false);
        draw(g,mouseX,mouseY,favorites,favoritePage,FAVORITES,true);
        if(hit(mouseX,mouseY,107,97,16,16))tip(g,"Deposit items for EMC. Charged Klein Stars empty here and can be taken back.",mouseX,mouseY);
        else if(hit(mouseX,mouseY,89,97,16,16))tip(g,"Forget: drag a learned icon here, or place an item to forget its exact variant. Physical items are returned.",mouseX,mouseY);
    }
    private void draw(GuiGraphicsExtractor g,int mouseX,int mouseY,List<String> keys,int current,int[][] positions,boolean pinned){
        for(int i=0;i<positions.length;i++){
            int[] p=positions[i];int index=current*positions.length+i;
            if(index>=keys.size()){
                if(pinned&&hit(mouseX,mouseY,p[0],p[1],16,16))tip(g,"Favorites: drag a learned item here, or middle click it in the right circle",mouseX,mouseY);
                continue;
            }
            String key=keys.get(index);ItemStack stack=exemplar(key);
            g.item(stack,left+p[0],top+p[1]);
            if(!pinned&&favorites.contains(key))g.text(font,"*",left+p[0]+11,top+p[1]-3,0xFFFFAA00,false);
            if(hit(mouseX,mouseY,p[0],p[1],16,16))tip(g,label(stack)+" · "+price(stack)+" EMC · Left: one · Right/Shift: stack · Middle: "+(pinned?"unpin":"favorite")+" · Wheel: page "+(current+1),mouseX,mouseY);
        }
    }
    @Override public void extractCarriedItem(GuiGraphicsExtractor g,int x,int y){
        super.extractCarriedItem(g,x,y);
        if(dragKey!=null&&known(dragKey)&&menu.getCarried().isEmpty())g.item(exemplar(dragKey),x-8,y-8);
    }
    private void tip(GuiGraphicsExtractor g,String text,int x,int y){g.setTooltipForNextFrame(font,Component.literal(text),x,y);}
    private boolean hit(double x,double y,int px,int py,int w,int h){return x>=left+px&&x<left+px+w&&y>=top+py&&y<top+py+h;}
    private boolean favoriteCircle(double x,double y){double dx=x-left-51,dy=y-top-58;return dx*dx+dy*dy<=45*45;}
    private boolean learnedCircle(double x,double y){double dx=x-left-166,dy=y-top-58;return dx*dx+dy*dy<=58*58;}
    private String entry(double x,double y,List<String> keys,int current,int[][] positions){
        for(int i=0;i<positions.length;i++)if(current*positions.length+i<keys.size()&&hit(x,y,positions[i][0],positions[i][1],16,16))return keys.get(current*positions.length+i);
        return null;
    }
    private String entry(double x,double y){String key=entry(x,y,favorites,favoritePage,FAVORITES);return key==null?entry(x,y,filtered,page,LEARNED):key;}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){
        double x=e.x(),y=e.y();int button=e.button();
        if(search!=null){
            if(hit(x,y,83,8,55,12)){
                dragKey=null;sweeping=false;sweptSlots.clear();
                TransmutationInput.setSearchFocused(this,search,true);
                return super.mouseClicked(e,twice);
            }
            TransmutationInput.setSearchFocused(this,search,false);
        }
        if(hit(x,y,116,108,8,8)&&page>0){page--;refreshList();return true;}
        if(hit(x,y,215,108,8,8)&&(page+1)*LEARNED.length<filtered.size()){page++;refreshList();return true;}
        if(hit(x,y,8,84,8,8)&&favoritePage>0){favoritePage--;refreshList();return true;}
        if(hit(x,y,84,84,8,8)&&(favoritePage+1)*FAVORITES.length<favorites.size()){favoritePage++;refreshList();return true;}
        if(!menu.getCarried().isEmpty()&&favoriteCircle(x,y)){send("pin_carried",0,"",1);return true;}
        if(menu.getCarried().isEmpty()&&button==InputConstants.MOUSE_BUTTON_LEFT&&hit(x,y,89,97,16,16)&&menu.getSlot(1).getItem().isEmpty()&&selectedKey!=null){send("forget",0,selectedKey,knowledgeRevision);selectedKey=null;return true;}
        String key=entry(x,y);
        if(key!=null&&menu.getCarried().isEmpty()){
            selectedKey=key;
            if(button==InputConstants.MOUSE_BUTTON_MIDDLE)send("favorite",0,key,1);
            else if(TransmutationInput.purchaseCount(button,e.modifiers())==64)send("buy",0,key,64);
            else if(button==InputConstants.MOUSE_BUTTON_LEFT){dragKey=key;dragStartX=x;dragStartY=y;}
            return true;
        }
        if((e.modifiers()&InputConstants.MOD_SHIFT)!=0&&button==InputConstants.MOUSE_BUTTON_LEFT&&inventorySlot(x,y)>=0){sweeping=true;sweptSlots.clear();sweep(x,y);return true;}
        return super.mouseClicked(e,twice);
    }
    private int inventorySlot(double x,double y){
        for(int i=0;i<36;i++){int row=i/9;int py=117+row*18+(row==3?4:0);if(hit(x,y,35+(i%9)*18,py,18,18))return i<27?i+9:i-27;}
        return -1;
    }
    private void sweep(double x,double y){int slot=inventorySlot(x,y);if(slot>=0&&sweptSlots.add(slot))send("sell",slot,"",64);}
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){if(dragKey!=null)return true;if(sweeping){sweep(e.x(),e.y());return true;}return super.mouseDragged(e,dx,dy);}
    @Override public boolean mouseReleased(MouseButtonEvent e){
        if(dragKey!=null){
            String key=dragKey;dragKey=null;
            if(hit(e.x(),e.y(),89,97,16,16)){send("forget",0,key,knowledgeRevision);selectedKey=null;}
            else if(favoriteCircle(e.x(),e.y())&&Math.hypot(e.x()-dragStartX,e.y()-dragStartY)>4)send("pin",0,key,1);
            else if(key.equals(entry(e.x(),e.y())))send("buy",0,key,1);
            return true;
        }
        if(sweeping){sweeping=false;sweptSlots.clear();return true;}
        if(!menu.getCarried().isEmpty()&&favoriteCircle(e.x(),e.y())){send("pin_carried",0,"",1);return true;}
        return super.mouseReleased(e);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        if(favoriteCircle(x,y)){favoritePage+=dy<0?1:-1;refreshList();return true;}
        if(learnedCircle(x,y)){page+=dy<0?1:-1;refreshList();return true;}
        return super.mouseScrolled(x,y,dx,dy);
    }
    private void send(String command,int slot,String key,int count){ClientPlayNetworking.send(new PEPackets.Action(tablet,position,command,slot,key,count));}
    @Override public boolean isPauseScreen(){return false;}
}
