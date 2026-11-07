package com.example.circlemage.client;
import com.example.circlemage.network.ModNetworking;
import com.example.circlemage.spell.*;
import com.example.circlemage.spell.SpellCatalog.Entry;
import com.example.circlemage.spell.SpellCatalog.School;
import com.example.circlemage.client.ClientPackets;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;

/** Original open-book art with school branches, tier rows and server-authoritative loadouts. */
public class MagicTreeScreen extends Screen {
    private static final Identifier BOOK=Identifier.fromNamespaceAndPath("circlemage","textures/gui/spellbook_bg.png");
    private static final int INK=0xFF382C22,DIM=0xFF857560;
    private int x,y,focus=0,pending=-1;private boolean notes=false;
    private Button bind,select;
    private record Node(SpellCatalog.Entry entry,int x,int y){}
    private final List<Node> nodes=new ArrayList<>();
    public MagicTreeScreen(){super(Component.literal("Magic Tree"));}
    @Override protected void init(){
        x=(width-340)/2;y=(height-220)/2;nodes.clear();
        for(var e:SpellCatalog.ALL){
            int row=switch(e.tier()){case 1->57;case 2->94;case 3->145;default->176;};
            if(e.spell()==Spell.STONE_DOME||e.spell()==Spell.DASH)row+=19;
            if(e.spell()==Spell.STEAM_EXPLOSION)continue;
            nodes.add(new Node(e,x+27+e.school().ordinal()*27,y+row));
        }
        bind=addRenderableWidget(Button.builder(Component.literal("Attune"),b->bindFocus(true)).bounds(x+188,y+162,122,18).build());
        select=addRenderableWidget(Button.builder(Component.literal("Select for R"),b->{SpellSelectorState.select(focus);onClose();}).bounds(x+188,y+183,122,18).build());
        addRenderableWidget(Button.builder(Component.literal("Notes"),b->{notes=!notes;}).bounds(x+28,y+204,46,14).build());
        addRenderableWidget(Button.builder(Component.literal("Done"),b->onClose()).bounds(x+267,y+204,43,14).build());
        tick();
    }
    private void bindFocus(boolean equip){var buf=FriendlyByteBufs.create();buf.writeInt(focus);buf.writeBoolean(equip);ClientPackets.send(ModNetworking.BIND_SPELL,buf);if(equip)pending=focus;}
    @Override public void tick(){
        if(pending>=0&&SpellSelectorState.usable(pending)){SpellSelectorState.select(pending);pending=-1;}
        var e=SpellCatalog.entry(focus);
        bind.visible=!notes&&e!=null&&e.tier()<=2;
        String error=SpellCatalog.bindError(ClientManaState.circle,ClientManaState.unlockedSpells,ClientManaState.boundSpells,ClientManaState.magicLimitBroken,focus);
        bind.active=error==null;
        bind.setTooltip(error==null?null:net.minecraft.client.gui.components.Tooltip.create(Component.literal(error)));
        bind.setMessage(Component.literal(SpellCatalog.bit(ClientManaState.boundSpells,focus)?"Unbind":"Attune / bind"));
        select.visible=!notes;select.active=SpellSelectorState.usable(focus);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor c,int mx,int my,float delta){
        c.fill(0, 0, width, height, 0xA0100D18);c.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,BOOK,x,y,0f,0f,340,220,340,220);
        c.centeredText(font,"ELEMENTAL TREE",x+94,y+18,0xFFEAD3A0);
        c.centeredText(font,"Circle "+ClientManaState.circle,x+249,y+20,0xFFEAD3A0);
        if(notes)renderNotes(c);else{
            for(var school:SpellCatalog.School.values()){int cx=x+37+school.ordinal()*27;c.fill(cx,y+49,cx+1,y+192,0x55483D31);c.centeredText(font,school.label.substring(0,1),cx,y+35,school.color);}
            for(Node node:nodes){
                var e=node.entry;boolean known=ClientManaState.isSpellUnlocked(e.spell().id),circle=ClientManaState.circle>=e.tier(),bound=SpellSelectorState.usable(e.spell().id);
                int color=!circle?0xFFC4B59B:!known?0xFFAA9A82:bound?e.school().color:0xFFE6D2AE;
                c.fill(node.x-1,node.y-1,node.x+24,node.y+17,e.spell().id==focus?0xFF755127:0xFF9B8664);c.fill(node.x,node.y,node.x+23,node.y+16,color);
                c.centeredText(font,abbreviation(e.spell()),node.x+11,node.y+4,INK);
            }
            for(int tier=1;tier<=4;tier++)c.text(font,"T"+tier,x+9,y+switch(tier){case 1->61;case 2->99;case 3->149;default->180;},DIM,false);
            c.text(font,"T1 "+SpellCatalog.countBound(ClientManaState.boundSpells,1)+"/"+(ClientManaState.magicLimitBroken?"*":"1")+"  T2 "+SpellCatalog.countBound(ClientManaState.boundSpells,2)+"/"+(ClientManaState.magicLimitBroken?"*":"3"),x+78,y+207,0xFFEAD3A0,false);
            renderDetails(c);
        }
        super.extractRenderState(c,mx,my,delta);
        if(!notes)for(Node node:nodes)if(mx>=node.x&&mx<node.x+23&&my>=node.y&&my<node.y+16)c.setComponentTooltipForNextFrame(font,List.of(Component.literal(node.entry.name()),Component.literal("T"+node.entry.tier()+" / "+node.entry.school().label),Component.literal(status(node.entry))),mx,my);
    }
    private String abbreviation(Spell s){return switch(s){case FIRE_BOLT->"FB";case ICE_VOLLEY->"IV";case STONE_SPIKE->"SS";case WIND_BLADE->"WB";case MAGIC_MISSILE->"MM";case FIREBALL->"AF";case WATER_BUBBLE->"BB";case STONE_FIST->"SF";case STONE_DOME->"SD";case WALL_OF_WIND->"WW";case DASH->"WE";case VINE_GRAB->"VG";case FLAME_REBIRTH->"FR";case SOOTHING_WATER->"SW";case TRACKING_MARK->"MK";default->"AM";};}
    private String status(SpellCatalog.Entry e){
        if(ClientManaState.circle<e.tier())return "Circle "+e.tier()+" required";
        if(!ClientManaState.isSpellUnlocked(e.spell().id))return e.spell()==Spell.CLEAR_MAGIC?"Observe a burning guardian's Dispel first":e.spell()==Spell.STEAM_EXPLOSION?"Learn Fireball + Ice Volley":"Learn from a "+e.school().label+" Tome";
        return SpellSelectorState.usable(e.spell().id)?"Ready / equipped":"Learned / not equipped";
    }
    private void renderDetails(GuiGraphicsExtractor c){
        var e=SpellCatalog.entry(focus);if(e==null)return;int yy=y+39;
        for(var line:font.split(Component.literal(e.name()),126)){c.text(font,line,x+187,yy,INK,false);yy+=11;}
        c.text(font,"T"+e.tier()+"  "+e.school().label,x+187,yy+3,0xFF75512D,false);yy+=17;
        c.text(font,e.spell().manaCost+" mana | CD "+String.format(java.util.Locale.ROOT,"%.1fs",e.spell().cooldownTicks/20.),x+187,yy,INK,false);yy+=14;
        for(var line:font.split(Component.literal(e.description()),126)){c.text(font,line,x+187,yy,DIM,false);yy+=10;}
        if(e.spell()==Spell.CLEAR_MAGIC)for(var line:font.split(Component.literal(ClientManaState.antiMagicEpiphany?"Epiphany obtained: 5% insight per successful spell.":"Ignite a tower guardian; witness its Dispel."),126)){c.text(font,line,x+187,yy+3,0xFF7F582F,false);yy+=10;}
        c.fill(x+28,y+193,x+159,y+202,focus==21?0xFFA99166:0xFFDBC9A8);
        c.text(font,"Steam: Fireball + Ice",x+30,y+193,ClientManaState.isSpellUnlocked(21)?0xFF356970:DIM,false);
    }
    private void renderNotes(GuiGraphicsExtractor c){
        String[] a={"M: channel mana","R: cast selected spell","Alt + wheel: change spell","J: tree / loadout","K: chants and arrays","G: hold for Dispel","Quick keys: Controls menu","","Click nodes for details.","Attune one T1 and 3 T2.","T3/T4 have no slot cap.","Tomes respect your circle.","Steam recipe requires T3."};
        String[] b={"Circle 1-3: gather mana.","Circle 4: ignite a guardian,","observe Dispel, then cast","spells for 5% insight rolls.","","Intact towers have guards.","Crafting items and tower","loot remain available.","","Steam blocks NPC sight.","Use Soothing Water before","entering the cloud.","Flame Rebirth clears webs."};
        for(int i=0;i<a.length;i++)c.text(font,a[i],x+26,y+42+i*11,INK,false);
        for(int i=0;i<b.length;i++)c.text(font,b[i],x+186,y+42+i*11,INK,false);
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) { double mx=event.x(), my=event.y(); int button=event.button();
        if(!notes){
            if(mx>=x+28&&mx<x+159&&my>=y+193&&my<y+203){focus=21;tick();return true;}
            for(Node n:nodes)if(mx>=n.x&&mx<n.x+23&&my>=n.y&&my<n.y+16){focus=n.entry.spell().id;if(button==1&&n.entry.tier()<=2)bindFocus(false);tick();return true;}
            if(button==0&&bind.visible&&bind.active&&mx>=bind.getX()&&mx<bind.getX()+bind.getWidth()&&my>=bind.getY()&&my<bind.getY()+bind.getHeight()){bindFocus(!SpellCatalog.bit(ClientManaState.boundSpells,focus));return true;}
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public void onClose(){ClientPackets.send(ModNetworking.BOOK_CLOSED,FriendlyByteBufs.empty());super.onClose();}
    @Override public boolean isPauseScreen(){return false;}
}
