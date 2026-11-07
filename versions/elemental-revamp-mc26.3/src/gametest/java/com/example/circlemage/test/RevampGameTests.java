package com.example.circlemage.test;

import com.example.circlemage.CircleMageMod;
import com.example.circlemage.entity.*;
import com.example.circlemage.item.ModItems;
import com.example.circlemage.mana.*;
import com.example.circlemage.spell.*;
import java.lang.reflect.Method;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Actual Minecraft server tests, including mixin behavior. Not shipped in the release. */
public class RevampGameTests {
    private static final String ARENA="circlemage_tests:arena";
    private static void close(GameTestHelper c,double got,double want,String label){c.assertTrue(Math.abs(got-want)<.001,label+": expected "+want+", got "+got);}
    private static Vec3 pos(GameTestHelper c,double x,double z){return c.absoluteVec(new Vec3(x,2,z));}
    private static <T extends Mob> T mob(GameTestHelper c,EntityType<T> type,double x,double z){
        T m=type.create(c.getLevel(),net.minecraft.world.entity.EntitySpawnReason.COMMAND);m.setPos(pos(c,x,z));m.setNoAi(true);m.setNoGravity(true);c.getLevel().addFreshEntity(m);return m;
    }
    private static ServerPlayer player(GameTestHelper c){
        var p=c.makeMockServerPlayerInLevel();p.setPos(pos(c,8,8));p.setNoGravity(true);
        ManaPlayer.get(p).setIntroStage(2);return p;
    }
    private static void invoke(String name,Class<?>[] signature,Object... args){
        try {var m=CircleMageMod.class.getDeclaredMethod(name,signature);m.setAccessible(true);m.invoke(new CircleMageMod(),args);}
        catch(ReflectiveOperationException e){throw new RuntimeException(e);}
    }
    private static void cast(ServerPlayer p,Spell s){invoke("handleCast",new Class<?>[]{ServerPlayer.class,int.class,boolean.class,int.class},p,s.id,false,0);}
    private static ArcaneEffectEntity effect(GameTestHelper c,LivingEntity owner,LivingEntity target,Vec3 point,int kind,int ticks){
        var e=new ArcaneEffectEntity(c.getLevel(),owner,target,point,kind,ticks);c.getLevel().addFreshEntity(e);return e;
    }

    @GameTest(structure=ARENA)
    public void loadoutAndTierRules(GameTestHelper c){
        ManaData fresh=new ManaData();c.assertTrue(fresh.getUnlockedSpells()==1&&fresh.getBoundSpells()==1,"Only Magic Missile is innate");
        int all=0;for(var e:SpellCatalog.ALL)all|=1<<e.spell().id;
        int bound=SpellCatalog.bind(1,11,false);
        c.assertTrue(SpellCatalog.countBound(bound,1)==1&&!SpellCatalog.bit(bound,0),"Replacing T1 must unbind the old spell");
        bound=SpellCatalog.bind(bound,14,false);bound=SpellCatalog.bind(bound,15,false);bound=SpellCatalog.bind(bound,16,false);
        c.assertTrue(SpellCatalog.bindError(2,all,bound,false,17)!=null,"Fourth T2 must be rejected");
        c.assertTrue(SpellCatalog.bindError(2,all,bound,true,17)==null,"Command bypass permits fourth T2");
        c.assertFalse(SpellCatalog.usable(1,all,bound,true,14),"Bypass must not skip circle requirement");
        c.assertFalse(SpellCatalog.usable(2,0,bound,true,14),"Bypass must not skip knowledge");
        c.assertTrue(SpellCatalog.usable(3,all,0,false,18),"T3 has no binding requirement");
        c.assertTrue(SpellCatalog.known((1<<11)|(1<<12),21),"Combination comes from both ingredients' knowledge");
        c.assertFalse(SpellCatalog.usable(2,all,0,true,21),"Steam requires T3");c.succeed();
    }

    @GameTest(structure=ARENA)
    public void savedLoadoutAndLegacyMigration(GameTestHelper c){
        ManaData d=new ManaData();d.setCircle(3);d.unlockSpell(12);d.setBoundSpells((1<<12)|(1<<14));d.setAntiMagicEpiphany(true);d.setMagicLimitBroken(true);
        CompoundTag n=new CompoundTag();d.writeNbt(n);ManaData restored=new ManaData();restored.readNbt(n);
        c.assertTrue(restored.getBoundSpells()==d.getBoundSpells()&&restored.hasAntiMagicEpiphany()&&restored.isMagicLimitBroken(),"V2 state must survive save/load");
        ManaData clone=new ManaData();clone.copyFrom(restored);c.assertTrue(clone.getBoundSpells()==d.getBoundSpells()&&clone.hasAntiMagicEpiphany(),"Death-copy must preserve loadout/insight");
        CompoundTag legacy=new CompoundTag();legacy.putInt("circle",2);legacy.putInt("unlockedSpells",(1<<9)|(1<<11));
        restored.readNbt(legacy);c.assertTrue(restored.isSpellUnlocked(0)&&restored.getBoundSpells()==1,"Old saves gain innate missile plus a safe default loadout");c.succeed();
    }

    @GameTest(structure=ARENA)
    public void circleProgressionAndInsightGate(GameTestHelper c){
        var p=player(c);var d=ManaPlayer.get(p);
        for(int circle=1;circle<=3;circle++){
            d.setCurrentMana(Circles.THRESHOLDS[circle]);invoke("tryAdvanceCircle",new Class<?>[]{ServerPlayer.class,ManaData.class},p,d);
            c.assertTrue(d.getCircle()==circle,"Circles 1-3 need only mana");
        }
        d.setCurrentMana(50000);invoke("tryAdvanceCircle",new Class<?>[]{ServerPlayer.class,ManaData.class},p,d);
        c.assertTrue(d.getCircle()==3,"Circle 4 must remain blocked without Dispel discovery");
        c.getLevel().getRandom().setSeed(0);
        for(int i=0;i<300;i++)invoke("doMagicUseRolls",new Class<?>[]{ServerPlayer.class,ManaData.class},p,d);
        c.assertFalse(d.isSpellUnlocked(3),"No Anti Magic rolls before epiphany");
        d.setAntiMagicEpiphany(true);c.getLevel().getRandom().setSeed(0);
        for(int i=0;i<300&&!d.isSpellUnlocked(3);i++)invoke("doMagicUseRolls",new Class<?>[]{ServerPlayer.class,ManaData.class},p,d);
        c.assertTrue(d.isSpellUnlocked(3),"Casting insight can unlock Anti Magic after epiphany");
        invoke("tryAdvanceCircle",new Class<?>[]{ServerPlayer.class,ManaData.class},p,d);
        c.assertTrue(d.getCircle()==4,"Discovery and mana must open circle 4");c.succeed();
    }

    @GameTest(structure=ARENA)
    public void authoritativeCastingAndCooldowns(GameTestHelper c){
        var p=player(c);var d=ManaPlayer.get(p);d.setCircle(1);d.setCurrentMana(1000);d.unlockSpell(18);
        cast(p,Spell.SOOTHING_WATER);close(c,d.getCurrentMana(),1000,"Locked T3 spends no mana");c.assertFalse(p.hasEffect(RevampStatusEffects.SOOTHING),"Forged packet cannot cast T3");
        d.setCircle(3);cast(p,Spell.SOOTHING_WATER);close(c,d.getCurrentMana(),550,"Valid spell cost");
        c.assertTrue(p.hasEffect(RevampStatusEffects.SOOTHING),"Valid T3 applies effect");
        cast(p,Spell.SOOTHING_WATER);close(c,d.getCurrentMana(),550,"Cooldown rejects repeat cast");
        d.setCircle(2);d.unlockSpell(9);d.setCurrentMana(1000);cast(p,Spell.DASH);close(c,d.getCurrentMana(),1000,"Unbound T2 rejected even through CAST fallback");
        c.succeed();
    }

    @GameTest(structure=ARENA)
    public void tomeConsumptionAndCommandBypass(GameTestHelper c){
        var p=player(c);p.setGameMode(GameType.SURVIVAL);var d=ManaPlayer.get(p);d.setCircle(1);
        var book=new ItemStack(ModItems.TOME_FIRE,3);p.setItemInHand(InteractionHand.MAIN_HAND,book);
        ModItems.TOME_FIRE.use(c.getLevel(),p,InteractionHand.MAIN_HAND);
        c.assertTrue(d.isSpellUnlocked(11)&&book.getCount()==2,"T1 tome teaches Fireball and consumes one");
        ModItems.TOME_FIRE.use(c.getLevel(),p,InteractionHand.MAIN_HAND);c.assertTrue(book.getCount()==2,"Exhausted current-tier tome is retained");
        d.setCircle(2);ModItems.TOME_FIRE.use(c.getLevel(),p,InteractionHand.MAIN_HAND);c.assertTrue(d.isSpellUnlocked(1)&&book.getCount()==1,"Same school tome teaches T2 when eligible");
        c.getLevel().getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER),"breakmagiclimit");
        c.assertTrue(d.isMagicLimitBroken(),"Real command sets the persistent bypass flag");c.succeed();
    }

    @GameTest(structure=ARENA,maxTicks=110)
    public void bubbleDamageAndExpiry(GameTestHelper c){
        var owner=mob(c,net.minecraft.world.entity.EntityTypes.COW,8,8);var target=mob(c,net.minecraft.world.entity.EntityTypes.COW,12,8);
        var bubble=effect(c,owner,target,target.getEyePosition(),ArcaneEffectEntity.BUBBLE,80);
        c.runAfterDelay(2,()->c.assertTrue(target.getAirSupply()==0,"Suffocation starts immediately"));
        c.runAfterDelay(82,()->{close(c,target.getHealth(),4,"Bubble totals exactly 6 HP");c.assertTrue(bubble.isRemoved()&&target.getAirSupply()==target.getMaxAirSupply(),"Bubble expires and restores air");c.succeed();});
    }

    @GameTest(structure=ARENA,maxTicks=40)
    public void groundAttackDamage(GameTestHelper c){
        var owner=mob(c,net.minecraft.world.entity.EntityTypes.COW,8,8);var spikeTarget=mob(c,net.minecraft.world.entity.EntityTypes.COW,14,8);var fistTarget=mob(c,net.minecraft.world.entity.EntityTypes.COW,20,8);
        effect(c,owner,spikeTarget,spikeTarget.position(),ArcaneEffectEntity.SPIKE,24);
        effect(c,owner,fistTarget,fistTarget.position(),ArcaneEffectEntity.FIST,24);
        c.runAfterDelay(10,()->{close(c,spikeTarget.getHealth(),7,"Spike 3 HP");close(c,fistTarget.getHealth(),5,"Fist 5 HP");c.succeed();});
    }

    @GameTest(structure=ARENA,maxTicks=100)
    public void iceVolleyDamageAndStackedSlow(GameTestHelper c){
        var owner=mob(c,net.minecraft.world.entity.EntityTypes.COW,8,8);var target=mob(c,net.minecraft.world.entity.EntityTypes.COW,11,8);
        double speed=target.getAttributeValue(Attributes.MOVEMENT_SPEED);
        for(int i=0;i<3;i++){
            var spike=new IceSpikeEntity(c.getLevel(),owner,new Vec3(1,0,0));spike.setPos(target.position().add(-1,.7,0));spike.setDeltaMovement(.6,0,0);c.getLevel().addFreshEntity(spike);
        }
        c.runAfterDelay(5,()->{close(c,target.getHealth(),7,"Three ice hits each deal 1 HP");close(c,target.getAttributeValue(Attributes.MOVEMENT_SPEED),speed*.85,"Three hits slow by 15 percent");});
        c.runAfterDelay(90,()->{c.assertFalse(target.hasEffect(RevampStatusEffects.FROSTED),"Frost expires");c.succeed();});
    }

    @GameTest(structure=ARENA,maxTicks=85)
    public void rootsImmobilizeAndExpire(GameTestHelper c){
        var target=mob(c,net.minecraft.world.entity.EntityTypes.COW,12,8);target.addEffect(new MobEffectInstance(RevampStatusEffects.ROOTED,60));target.setDeltaMovement(1,0,1);
        c.runAfterDelay(2,()->{close(c,target.getAttributeValue(Attributes.MOVEMENT_SPEED),0,"Root removes movement speed");close(c,target.getDeltaMovement().horizontalDistance(),0,"Root removes horizontal knockback velocity");});
        c.runAfterDelay(62,()->{c.assertFalse(target.hasEffect(RevampStatusEffects.ROOTED),"Root lasts 3 seconds");c.succeed();});
    }

    @GameTest(structure=ARENA,maxTicks=660)
    public void steamDamageSightAndLifetime(GameTestHelper c){
        var owner=mob(c,net.minecraft.world.entity.EntityTypes.COW,8,8);var target=mob(c,net.minecraft.world.entity.EntityTypes.IRON_GOLEM,14,8);var observer=mob(c,net.minecraft.world.entity.EntityTypes.ZOMBIE,22,8);
        observer.setTarget(target);
        var steam=effect(c,owner,null,target.position().add(0,1,0),ArcaneEffectEntity.STEAM,620);
        c.runAfterDelay(25,()->{
            c.assertFalse(observer.hasLineOfSight(target),"Steam blocks NPC line of sight");c.assertTrue(observer.getTarget()==null,"Steam clears existing NPC lock");
            observer.setTarget(target);c.assertTrue(observer.getTarget()==null,"NPC cannot reacquire in steam");
        });
        c.runAfterDelay(622,()->{close(c,target.getHealth(),85,"15 steam pulses of 1 HP");c.assertTrue(steam.isRemoved(),"30-second cloud expires after formation");c.succeed();});
    }

    @GameTest(structure=ARENA,maxTicks=100)
    public void soothingFireAndSteamImmunity(GameTestHelper c){
        var p=player(c);var d=ManaPlayer.get(p);d.setCircle(3);d.setCurrentMana(1000);d.unlockSpell(18);
        var steam=effect(c,p,null,p.getEyePosition(),ArcaneEffectEntity.STEAM,620);
        double[] afterCast={0};
        c.runAfterDelay(25,()->{
            double before=d.getCurrentMana();cast(p,Spell.SOOTHING_WATER);close(c,d.getCurrentMana(),before,"Cannot cast Soothing Water inside steam");
            steam.discard();cast(p,Spell.SOOTHING_WATER);c.assertTrue(p.hasEffect(RevampStatusEffects.SOOTHING),"Cast outside steam succeeds");
            afterCast[0]=d.getCurrentMana();
            p.setGameMode(GameType.SURVIVAL);p.setRemainingFireTicks(100);c.assertFalse(p.isOnFire(),"Soothing prevents ignition");
            p.hurt(c.getLevel().damageSources().inFire(),4);close(c,p.getHealth(),20,"Soothing negates fire damage");
            effect(c,p,null,p.getEyePosition(),ArcaneEffectEntity.STEAM,620);
        });
        c.runAfterDelay(90,()->{close(c,p.getHealth(),20,"Soothing negates steam pulses");close(c,d.getCurrentMana()-afterCast[0],65*3.*2/20,"Soothing doubles actual passive mana flow");c.succeed();});
    }

    @GameTest(structure=ARENA)
    public void rebirthCleansesWithoutChangingTerrain(GameTestHelper c){
        var p=player(c);p.setGameMode(GameType.SURVIVAL);p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,100));p.addEffect(new MobEffectInstance(MobEffects.SPEED,100));
        p.setRemainingFireTicks(100);var bubble=effect(c,p,p,p.getEyePosition(),ArcaneEffectEntity.BUBBLE,80);
        var floor=p.blockPosition().below();c.getLevel().setBlockAndUpdate(floor,Blocks.STONE.defaultBlockState());c.getLevel().setBlockAndUpdate(p.blockPosition(),Blocks.COBWEB.defaultBlockState());
        RevampMagic.cast(p,Spell.FLAME_REBIRTH);
        close(c,p.getHealth(),17,"Flame Rebirth exact 3 HP payment");c.assertFalse(p.hasEffect(MobEffects.SLOWNESS)||p.isOnFire(),"Harmful effects/fire cleansed");
        c.assertTrue(p.hasEffect(MobEffects.SPEED),"Beneficial effects retained");c.assertTrue(bubble.isRemoved(),"Suffocation bubble removed");
        c.assertTrue(c.getLevel().getBlockState(p.blockPosition()).isAir()&&c.getLevel().getBlockState(floor).is(Blocks.STONE),"Only cobweb is removed");
        p.setHealth(3);c.assertTrue(RevampMagic.failure(p,Spell.FLAME_REBIRTH)!=null,"Rebirth refuses lethal payment");c.succeed();
    }

    @GameTest(structure=ARENA,maxTicks=30)
    public void guardianDispelGrantsEpiphany(GameTestHelper c){
        var p=player(c);var guard=new WanderingMageEntity(ModEntities.WANDERING_MAGE,c.getLevel());guard.setPos(pos(c,12,8));guard.setNoGravity(true);
        guard.configureForSpawn(1,false);guard.configureAsGuard(guard.position(),10);c.getLevel().addFreshEntity(guard);guard.igniteForSeconds(5);
        c.runAfterDelay(5,()->{c.assertFalse(guard.isOnFire(),"Guardian dispels ignition");c.assertTrue(ManaPlayer.get(p).hasAntiMagicEpiphany(),"Nearby witness receives persistent epiphany");c.succeed();});
    }

    @GameTest(structure=ARENA,maxTicks=160)
    public void domeContainmentDispelAndExpiry(GameTestHelper c){
        var target=mob(c,net.minecraft.world.entity.EntityTypes.COW,14,8);var center=target.position();var dome=effect(c,target,target,center,ArcaneEffectEntity.DOME,120);
        var outsider=mob(c,net.minecraft.world.entity.EntityTypes.COW,14,8);
        target.setPos(center.add(3,0,0));
        c.runAfterDelay(2,()->{c.assertTrue(target.position().distanceTo(center)<2.8,"Dome retains its original occupants");
            c.assertTrue(outsider.position().distanceTo(center)>=3,"Dome rejects new entrants even at its center");target.setPos(center.add(0,4,0));});
        c.runAfterDelay(4,()->c.assertTrue(target.getY()-center.y+target.getBbHeight()<=2.81,"Dome stops a straight-up escape through its center"));
        c.runAfterDelay(122,()->{c.assertTrue(dome.isRemoved(),"Stone Dome disappears without terrain blocks");
            var other=effect(c,target,target,center,ArcaneEffectEntity.DOME,120);ArcaneEffectEntity.dispel(c.getLevel(),target);c.assertTrue(other.isRemoved(),"Dispel removes a containing dome");c.succeed();});
    }

    @GameTest(structure=ARENA)
    public void towerTemplatesPreserveExteriorTerrain(GameTestHelper c){
        var world=c.getLevel();var origin=c.absolutePos(new BlockPos(2,2,2));
        for(String name:new String[]{"mage_tower","broken_tower_v1","broken_tower_v2","mage_tower_special"}){
            world.setBlockAndUpdate(origin,Blocks.DIAMOND_BLOCK.defaultBlockState());
            var template=world.getStructureTemplateManager().get(net.minecraft.resources.Identifier.fromNamespaceAndPath("circlemage",name));
            c.assertTrue(template.isPresent(),"Imported tower template loads: "+name);
            c.assertTrue(template.get().placeInWorld(world,origin,origin,new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setIgnoreEntities(true),world.getRandom(),2),"Template places: "+name);
            c.assertTrue(world.getBlockState(origin).is(Blocks.DIAMOND_BLOCK),"Exterior air margin must preserve terrain: "+name);
        }
        c.succeed();
    }

    @GameTest(structure=ARENA)
    public void earthFindsFloorInsteadOfCeiling(GameTestHelper c){
        var point=pos(c,12,8);var block=BlockPos.containing(point);var w=c.getLevel();
        w.setBlockAndUpdate(block.below(),Blocks.STONE.defaultBlockState());w.setBlockAndUpdate(block.above(2),Blocks.STONE.defaultBlockState());
        var ground=RevampMagic.ground(w,point);c.assertTrue(ground!=null,"Floor is valid earth-magic support");
        close(c,ground.y,point.y,"Earth spells erupt at the feet rather than an overhead ceiling");c.succeed();
    }
}
