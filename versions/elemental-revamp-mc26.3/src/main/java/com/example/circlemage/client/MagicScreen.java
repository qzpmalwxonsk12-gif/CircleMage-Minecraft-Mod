package com.example.circlemage.client;
import com.example.circlemage.spell.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Retained chants/arrays without obsolete spell-form controls. */
public class MagicScreen extends Screen {
    public MagicScreen(){super(Component.literal("Magehood"));}
    @Override protected void init(){
        int left=width/2-128,top=58;
        if(ClientManaState.chantUnlocked)for(var chant:PassiveChant.values())addRenderableWidget(Button.builder(Component.literal(chant.displayName),b->CircleMageClient.selectChant(chant.ordinal())).bounds(left,top+chant.ordinal()*23,122,20).build());
        if(ClientManaState.arrayUnlocked)for(var array:ArrayType.values())addRenderableWidget(Button.builder(Component.literal(array.displayName),b->CircleMageClient.selectArray(array.ordinal())).bounds(left+136,top+array.ordinal()*23,122,20).build());
        addRenderableWidget(Button.builder(Component.literal("Magic tree"),b->minecraft.gui.setScreen(new MagicTreeScreen())).bounds(width/2-64,height-33,128,20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor c,int mx,int my,float d){c.fill(0, 0, width, height, 0xA0100D18);c.centeredText(font,title,width/2,20,0xFFE6D0AD);c.centeredText(font,ClientManaState.chantUnlocked?"Chants":"Chants: discover at circle 4",width/2-66,42,0xFFBB99D8);c.centeredText(font,ClientManaState.arrayUnlocked?"Arrays":"Arrays: discover at circle 3",width/2+66,42,0xFFBB99D8);super.extractRenderState(c,mx,my,d);}
    @Override public boolean isPauseScreen(){return false;}
}
