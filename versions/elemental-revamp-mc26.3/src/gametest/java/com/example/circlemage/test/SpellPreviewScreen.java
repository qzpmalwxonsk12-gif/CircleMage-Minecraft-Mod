package com.example.circlemage.test;
import com.example.circlemage.entity.*;
import com.example.circlemage.spell.RevampStatusEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.effect.MobEffectInstance;
/** Builds a gallery in the isolated test world so renderers see actual networked entities. */
public final class SpellPreviewScreen {
    public static void spawn(ServerPlayer player) {
        var level=player.level();
        for(int kind=0;kind<6;kind++) {
            var point=player.position().add((kind-2.5)*4,0,10);
            var cow=EntityTypes.COW.create(level,EntitySpawnReason.COMMAND);
            cow.setPos(point); cow.setNoAi(true); cow.setPermanentlyInvulnerable(true); level.addFreshEntity(cow);
            if(kind==ArcaneEffectEntity.VINES)cow.addEffect(new MobEffectInstance(RevampStatusEffects.ROOTED,1200));
            level.addFreshEntity(new ArcaneEffectEntity(level,player,cow,point,kind,1200));
        }
        var book=new TutorialBookEntity(ModEntities.TUTORIAL_BOOK,level);book.setPos(player.position().add(0,0,3));level.addFreshEntity(book);
        var mage=new WanderingMageEntity(ModEntities.WANDERING_MAGE,level); mage.setPos(player.position().add(-3,0,5)); mage.configureForSpawn(2,false); mage.setNoAi(true); level.addFreshEntity(mage);
        var master=new MasterEntity(ModEntities.MASTER,level); master.setPos(player.position().add(3,0,5)); master.setNoAi(true); level.addFreshEntity(master);
    }
}
