package com.example.circlemage.client;
import com.example.circlemage.spell.*;
import com.example.circlemage.spell.SpellCatalog.Entry;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
public final class SpellSelectorState {
    private static int selected=0;private static final long[] cooldowns=new long[32];
    private SpellSelectorState(){}
    public static List<Integer> available(){return SpellCatalog.ALL.stream().map(e->e.spell().id).filter(SpellSelectorState::usable).toList();}
    public static boolean usable(int id){return SpellCatalog.usable(ClientManaState.circle,ClientManaState.unlockedSpells,ClientManaState.boundSpells,ClientManaState.magicLimitBroken,id);}
    public static void select(int id){if(usable(id))selected=id;}
    public static void reset(){selected=0;java.util.Arrays.fill(cooldowns,0);}
    public static int selectedSpellId(){var list=available();if(list.isEmpty())return -1;if(!list.contains(selected))selected=list.get(0);return selected;}
    public static int selectedIndex(){return Math.max(0,available().indexOf(selectedSpellId()));}
    public static void cycle(int dir){var list=available();if(list.isEmpty())return;selected=list.get(Math.floorMod(selectedIndex()+dir,list.size()));var c=Minecraft.getInstance();if(c.player!=null)c.gui.hud.setOverlayMessage(Component.literal(displayName(selected)),false);}
    public static boolean isChantable(int id){return false;}
    public static String displayName(int id){var e=SpellCatalog.entry(id);return e==null?"Magic":e.name();}
    public static Identifier icon(int id){String name=switch(id){case 1->"icon_fireball";case 3->"icon_anti";case 4->"icon_wind";case 9->"icon_dash";case 10->"icon_blade";case 11,20->"icon_firebolt";case 12,16,18,21->"icon_slow";case 13,14,15->"icon_barrier";case 17->"icon_wind";case 19->"icon_thunder";default->"icon_bolt";};return Identifier.fromNamespaceAndPath("circlemage","textures/gui/spell/"+name+".png");}
    public static void markCast(int id){var c=Minecraft.getInstance();Spell s=Spell.byId(id);if(c.level!=null&&s!=null&&id<32)cooldowns[id]=c.level.getGameTime()+Math.round(s.cooldownTicks*(ClientManaState.circle>=5?.85:1));}
    public static float cooldownFraction(int id){var c=Minecraft.getInstance();Spell s=Spell.byId(id);if(c.level==null||s==null||id<0||id>=32)return 0;return (float)Math.max(0,Math.min(1,(cooldowns[id]-c.level.getGameTime())/Math.max(1.,s.cooldownTicks*(ClientManaState.circle>=5?.85:1))));}
}
