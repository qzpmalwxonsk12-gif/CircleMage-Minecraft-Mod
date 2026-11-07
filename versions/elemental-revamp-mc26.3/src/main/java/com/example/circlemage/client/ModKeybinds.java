package com.example.circlemage.client;
import com.example.circlemage.network.ModNetworking;
import com.example.circlemage.spell.SpellCatalog;
import com.example.circlemage.spell.SpellCatalog.Entry;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import com.example.circlemage.client.ClientPackets;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

import java.util.LinkedHashMap;
import java.util.Map;

/** J owns the journal/tree. Quick keys are optional; server loadouts govern every input route. */
public final class ModKeybinds {
    public static final KeyMapping.Category CATEGORY=KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("circlemage","magic"));
    public static KeyMapping channelKey,castKey,nextSpellKey,prevSpellKey,selectModifierKey,carveKey,activateLawKey,magicMenuKey,lawsMenuKey,journalKey;
    private static final Map<Integer,KeyMapping> QUICK=new LinkedHashMap<>();
    private static int antiHeld;private static boolean antiFired,reservedJ;
    private ModKeybinds(){}
    public static void register(){
        channelKey=key("channel",InputConstants.KEY_M);castKey=key("cast",InputConstants.KEY_R);
        nextSpellKey=key("next_spell",InputConstants.UNKNOWN.getValue());prevSpellKey=key("prev_spell",InputConstants.UNKNOWN.getValue());
        selectModifierKey=key("select_modifier",InputConstants.KEY_LALT);
        carveKey=key("carve",InputConstants.KEY_C);activateLawKey=key("activate_law",InputConstants.KEY_B);
        magicMenuKey=key("magic_menu",InputConstants.KEY_K);lawsMenuKey=key("laws_menu",InputConstants.KEY_L);
        journalKey=key("magic_tree",InputConstants.KEY_J);
        for(var e:SpellCatalog.ALL)QUICK.put(e.spell().id,key("quick."+e.spell().id,e.spell().id==3?InputConstants.KEY_G:InputConstants.UNKNOWN.getValue()));
        ClientTickEvents.END_CLIENT_TICK.register(ModKeybinds::tick);
    }
    private static KeyMapping key(String name,int code){return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.circlemage."+name,InputConstants.Type.KEYBOARD,code,CATEGORY));}
    public static boolean isPriming(){return antiHeld>0&&!antiFired;}
    public static int primingSpellId(){return 3;}
    public static float primeProgress(){return Math.min(1,antiHeld/15f);}
    private static void tick(Minecraft c){
        if(!reservedJ){
            for(KeyMapping key:c.options.keyMappings)if(key!=journalKey&&KeyMappingHelper.getBoundKeyOf(key).getValue()==InputConstants.KEY_J)key.setKey(InputConstants.UNKNOWN);
            KeyMapping.resetMapping();reservedJ=true;
        }
        if(c.player==null||c.level==null){antiHeld=0;antiFired=false;SpellSelectorState.reset();return;}
        if(c.gui.screen()!=null){antiHeld=0;antiFired=false;return;}
        while(journalKey.consumeClick()){ClientPackets.send(ModNetworking.REQUEST_JOURNAL,FriendlyByteBufs.empty());c.gui.setScreen(new MagicTreeScreen());}
        if(c.gui.screen()!=null)return;
        while(channelKey.consumeClick())ClientPackets.send(ModNetworking.CHANNEL_TOGGLE,FriendlyByteBufs.empty());
        while(magicMenuKey.consumeClick())c.gui.setScreen(new MagicScreen());
        while(lawsMenuKey.consumeClick())c.gui.setScreen(new LawSelectionScreen());
        while(activateLawKey.consumeClick())ClientPackets.send(ModNetworking.ACTIVATE_LAW,FriendlyByteBufs.empty());
        while(carveKey.consumeClick())ClientPackets.send(ModNetworking.CARVE,FriendlyByteBufs.empty());
        while(nextSpellKey.consumeClick())SpellSelectorState.cycle(1);
        while(prevSpellKey.consumeClick())SpellSelectorState.cycle(-1);
        while(castKey.consumeClick()){int id=SpellSelectorState.selectedSpellId();if(id>=0&&id!=3)cast(c,id);}
        for(var entry:QUICK.entrySet())while(entry.getValue().consumeClick())if(entry.getKey()!=3)cast(c,entry.getKey());
        boolean held=QUICK.get(3).isDown()||(castKey.isDown()&&SpellSelectorState.selectedSpellId()==3);
        if(held){if(!antiFired&&++antiHeld>=15){cast(c,3);antiFired=true;}}else{antiHeld=0;antiFired=false;}
    }
    public static void cast(Minecraft c,int id){
        if(id==9){FriendlyByteBuf b=FriendlyByteBufs.create();b.writeFloat(c.player.input.getMoveVector().y);b.writeFloat(c.player.input.getMoveVector().x);ClientPackets.send(ModNetworking.DASH,b);}
        else{FriendlyByteBuf b=FriendlyByteBufs.create();b.writeInt(id);b.writeBoolean(false);b.writeInt(0);ClientPackets.send(ModNetworking.CAST,b);}
        SpellSelectorState.markCast(id);
    }
}
