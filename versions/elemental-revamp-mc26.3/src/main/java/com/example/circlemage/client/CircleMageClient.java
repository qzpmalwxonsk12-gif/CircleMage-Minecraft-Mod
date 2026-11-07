package com.example.circlemage.client;

import com.example.circlemage.entity.ModEntities;
import com.example.circlemage.item.WandItem.WandCrystal;
import com.example.circlemage.item.WandItem.WandMaterial;
import com.example.circlemage.network.ModNetworking;
import com.example.circlemage.particle.ModParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.example.circlemage.client.ClientPackets;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

public class CircleMageClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ModKeybinds.register();

        EntityRendererRegistry.register(ModEntities.MAGIC_MISSILE, MagicMissileRenderer::new);
        EntityRendererRegistry.register(ModEntities.ICE_SPIKE, IceSpikeRenderer::new);
        EntityRendererRegistry.register(ModEntities.ARCANE_EFFECT, ArcaneEffectRenderer::new);
        EntityRendererRegistry.register(ModEntities.MAGIC_BARRIER, MagicBarrierRenderer::new);
        EntityRendererRegistry.register(ModEntities.DARK_ORB, DarkOrbRenderer::new);
        EntityRendererRegistry.register(ModEntities.SPATIAL_REND, SpatialRendRenderer::new);
        EntityRendererRegistry.register(ModEntities.WIND_BLADE, WindBladeRenderer::new);
        EntityRendererRegistry.register(ModEntities.TUTORIAL_BOOK, TutorialBookRenderer::new);
        ModelLayerRegistry.registerModelLayer(WanderingMageRenderer.MAGE_LAYER,
                WanderingMageRenderer::getTexturedModelData);
        EntityRendererRegistry.register(ModEntities.WANDERING_MAGE, WanderingMageRenderer::new);
        ModelLayerRegistry.registerModelLayer(MasterRenderer.MASTER_LAYER,
                MasterRenderer::getTexturedModelData);
        EntityRendererRegistry.register(ModEntities.MASTER, MasterRenderer::new);

        registerParticles();

        // Arcane Station assembly screen (v2.1 wand-making framework).
        net.minecraft.client.gui.screens.MenuScreens.register(
                com.example.circlemage.block.ModBlocks.ARCANE_STATION_SCREEN_HANDLER, ArcaneStationScreen::new);

        // Bottom-of-screen mana bar (right of hotbar) and spell selector (left of hotbar).
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("circlemage","mana"), (gui,delta) -> ManaHudOverlay.render(gui,delta.getGameTimeDeltaPartialTick(false)));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("circlemage","spells"), (gui,delta) -> SpellHudOverlay.render(gui,delta.getGameTimeDeltaPartialTick(false)));
        // World-space casting circles (local charge-up + Wandering Mage telegraphs).
        CastingCircleRenderer.register();

        // Receive mana state from the server.
        ClientPackets.registerGlobalReceiver(ModNetworking.MANA_SYNC, (client, handler, buf, sender) -> {
            double currentMana = buf.readDouble();
            int circle = buf.readInt();
            long threshold = buf.readLong();
            boolean channeling = buf.readBoolean();
            int unlockedBuffs = buf.readInt();
            boolean arrayUnlocked = buf.readBoolean();
            int selectedArray = buf.readInt();
            boolean chantUnlocked = buf.readBoolean();
            int selectedChant = buf.readInt();
            boolean revertSpear = buf.readBoolean();
            boolean revertDarkOrb = buf.readBoolean();
            boolean lawsUnlocked = buf.readBoolean();
            int selectedLaw = buf.readInt();
            int unlockedLaws = buf.readInt();
            int unlockedSpells = buf.readInt();
            int introStage = buf.readInt();
            int boundSpells = buf.readInt();
            boolean antiMagicEpiphany = buf.readBoolean();
            boolean magicLimitBroken = buf.readBoolean();
            client.execute(() -> ClientManaState.update(currentMana, circle, threshold, channeling,
                    unlockedBuffs, arrayUnlocked, selectedArray, chantUnlocked, selectedChant,
                    revertSpear, revertDarkOrb, lawsUnlocked, selectedLaw, unlockedLaws, unlockedSpells,
                    introStage, boundSpells, antiMagicEpiphany, magicLimitBroken));
        });

        // Mage Journal snapshot.
        ClientPackets.registerGlobalReceiver(ModNetworking.JOURNAL_SYNC, (client, handler, buf, sender) -> {
            int circle = buf.readInt();
            boolean antiMagic = buf.readBoolean();
            boolean combination = buf.readBoolean();
            boolean arrays = buf.readBoolean();
            boolean chants = buf.readBoolean();
            boolean laws = buf.readBoolean();
            int unlockedLaws = buf.readInt();
            int realmBuffs = buf.readInt();
            boolean encounteredMage = buf.readBoolean();
            boolean prodigyDefeated = buf.readBoolean();
            boolean masterDefeated = buf.readBoolean();
            boolean lichDefeated = buf.readBoolean();
            client.execute(() -> ClientJournalState.update(circle, antiMagic, combination, arrays, chants,
                    laws, unlockedLaws, realmBuffs, encounteredMage, prodigyDefeated, masterDefeated, lichDefeated));
        });

        // Hardcore Law of Life "give up?" screen.
        ClientPackets.registerGlobalReceiver(ModNetworking.GIVE_UP, (client, handler, buf, sender) ->
                client.execute(() -> client.gui.setScreen(new GiveUpScreen())));

        // A single rank line at the top of the inventory (e.g. "4 circle mage (4 realm buffs)").
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof InventoryScreen) {
                ScreenEvents.afterExtract(screen).register((s, context, mx, my, td) -> {
                    if (ClientManaState.circle < 1) {
                        return;
                    }
                    String line = ClientManaState.circle + " circle mage"
                            + (ClientManaState.unlockedBuffs > 0
                            ? " (" + ClientManaState.unlockedBuffs + " realm buff"
                            + (ClientManaState.unlockedBuffs == 1 ? "" : "s") + ")" : "");
                    var tr = client.font;
                    context.text(tr, Component.literal(line).withStyle(ChatFormatting.LIGHT_PURPLE),
                            (w - tr.width(line)) / 2, 6, 0xFFFFFFFF, true);
                });
            }
        });

        // Camera shake from spell detonations.
        ClientPackets.registerGlobalReceiver(ModNetworking.SHAKE, (client, handler, buf, sender) -> {
            float intensity = buf.readFloat();
            int ticks = buf.readInt();
            client.execute(() -> ClientShakeState.set(intensity, ticks));
        });
        // Impact frame (white flash + brief desaturation) from the Hollow Purple detonation.
        ClientPackets.registerGlobalReceiver(ModNetworking.IMPACT, (client, handler, buf, sender) -> {
            int ticks = buf.readInt();
            client.execute(() -> ClientImpactState.trigger(ticks));
        });
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            ClientShakeState.tick();
            ClientImpactState.tick();
        });
        // White blowout flash drawn over everything during the impact frame.
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("circlemage","impact"), (context, tickDelta) -> {
            float a = ClientImpactState.flashAlpha();
            if (a <= 0.01f) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            int alpha = (int) (Math.min(1.0f, a) * 255);
            context.fill(0, 0, w, h, (alpha << 24) | 0x00FFFFFF);
        });

    }

    /** Bind every custom particle type to the shared {@link MagicGlowParticle} with its own behaviour. */
    private void registerParticles() {
        ParticleProviderRegistry reg = ParticleProviderRegistry.getInstance();
        // r, g, b, scaleStart, scaleEnd, gravity, rise, jitter, lifeMin, lifeMax, spin, fadeIn
        reg.register(ModParticles.ARCANE, sprites -> new MagicGlowParticle.Factory(sprites,
                new MagicGlowParticle.Settings(1f, 1f, 1f, 0.16f, 0.02f, 0f, 0.01f, 0.02f, 10, 18, 0.12f, false)));
        reg.register(ModParticles.ARCANE_TRAIL, sprites -> new MagicGlowParticle.Factory(sprites,
                new MagicGlowParticle.Settings(0.8f, 0.95f, 1f, 0.13f, 0.0f, 0f, 0f, 0.01f, 6, 10, 0f, false)));
        reg.register(ModParticles.EMBER, sprites -> new MagicGlowParticle.Factory(sprites,
                new MagicGlowParticle.Settings(1f, 1f, 1f, 0.17f, 0.03f, -0.4f, 0.04f, 0.03f, 16, 28, 0.05f, false)));
        reg.register(ModParticles.FROST, sprites -> new MagicGlowParticle.Factory(sprites,
                new MagicGlowParticle.Settings(1f, 1f, 1f, 0.15f, 0.02f, 0.4f, -0.01f, 0.02f, 18, 30, 0.06f, false)));
        reg.register(ModParticles.SPARK, sprites -> new MagicGlowParticle.Factory(sprites,
                new MagicGlowParticle.Settings(1f, 1f, 1f, 0.20f, 0.0f, 0.2f, 0f, 0.14f, 5, 9, 0.25f, false)));
        reg.register(ModParticles.GALE, sprites -> new MagicGlowParticle.Factory(sprites,
                new MagicGlowParticle.Settings(1f, 1f, 1f, 0.22f, 0.05f, 0f, 0f, 0.06f, 12, 20, 0.03f, false)));
        reg.register(ModParticles.RADIANCE, sprites -> new MagicGlowParticle.Factory(sprites,
                new MagicGlowParticle.Settings(1f, 1f, 1f, 0.12f, 0.55f, 0f, 0f, 0.02f, 12, 20, 0.04f, true)));
        reg.register(ModParticles.BARRIER_SHARD, sprites -> new MagicGlowParticle.Factory(sprites,
                new MagicGlowParticle.Settings(1f, 1f, 1f, 0.16f, 0.30f, 0f, 0f, 0.03f, 10, 18, 0.15f, true)));
        // Element runes share the white rune.png, tinted per element.
        reg.register(ModParticles.RUNE_FIRE, sprites -> new MagicGlowParticle.Factory(sprites,
                new MagicGlowParticle.Settings(1f, 0.55f, 0.18f, 0.45f, 0.45f, 0f, 0f, 0f, 24, 36, 0.04f, true)));
        reg.register(ModParticles.RUNE_THUNDER, sprites -> new MagicGlowParticle.Factory(sprites,
                new MagicGlowParticle.Settings(1f, 0.92f, 0.35f, 0.45f, 0.45f, 0f, 0f, 0f, 24, 36, 0.04f, true)));
        reg.register(ModParticles.RUNE_WIND, sprites -> new MagicGlowParticle.Factory(sprites,
                new MagicGlowParticle.Settings(0.7f, 1f, 0.8f, 0.45f, 0.45f, 0f, 0f, 0f, 24, 36, 0.04f, true)));
        reg.register(ModParticles.RUNE_BIND, sprites -> new MagicGlowParticle.Factory(sprites,
                new MagicGlowParticle.Settings(0.78f, 0.45f, 1f, 0.45f, 0.45f, 0f, 0f, 0f, 24, 36, 0.04f, true)));
        reg.register(ModParticles.BIND_MOTE, sprites -> new MagicGlowParticle.Factory(sprites,
                new MagicGlowParticle.Settings(0.78f, 0.45f, 1f, 0.16f, 0.02f, 0.1f, 0.02f, 0.04f, 14, 22, 0.1f, false)));
        // Spear of Light trail: gold-white sparks.
        reg.register(ModParticles.LIGHT, sprites -> new MagicGlowParticle.Factory(sprites,
                new MagicGlowParticle.Settings(1f, 0.92f, 0.7f, 0.15f, 0.0f, 0f, 0.01f, 0.02f, 8, 14, 0.1f, false)));
        // Dark Orb void motes.
        reg.register(ModParticles.VOID, sprites -> new MagicGlowParticle.Factory(sprites,
                new MagicGlowParticle.Settings(1f, 1f, 1f, 0.18f, 0.02f, 0.05f, 0f, 0.03f, 14, 24, 0.08f, false)));
        // Aftermath smoke: large, slow, long-lived, drifts upward.
        reg.register(ModParticles.SMOKE, sprites -> new MagicGlowParticle.Factory(sprites,
                new MagicGlowParticle.Settings(1f, 1f, 1f, 0.5f, 0.95f, -0.06f, 0.02f, 0.02f, 50, 80, 0.02f, true)));
        // Debris dust: small, falls, medium life.
        reg.register(ModParticles.DUST, sprites -> new MagicGlowParticle.Factory(sprites,
                new MagicGlowParticle.Settings(1f, 1f, 1f, 0.14f, 0.03f, 0.5f, 0f, 0.05f, 18, 34, 0.05f, false)));
    }

    /** Tell the server which array to carve, and update the local mirror immediately for snappy UI. */
    static void selectArray(int arrayOrdinal) {
        if (!ClientManaState.arrayUnlocked) {
            return;
        }
        ClientManaState.selectedArray = arrayOrdinal;
        FriendlyByteBuf buf = FriendlyByteBufs.create();
        buf.writeInt(arrayOrdinal);
        ClientPackets.send(ModNetworking.SELECT_ARRAY, buf);
    }

    static void selectChant(int chantOrdinal) {
        if (!ClientManaState.chantUnlocked) {
            return;
        }
        ClientManaState.selectedChant = chantOrdinal;
        FriendlyByteBuf buf = FriendlyByteBufs.create();
        buf.writeInt(chantOrdinal);
        ClientPackets.send(ModNetworking.SELECT_CHANT, buf);
    }

    /** Toggle a transformed spell back to / forward from its base form (baseSpellId 0 = bolt, 2 = slow). */
    static void toggleForm(int baseSpellId) {
        boolean revert;
        if (baseSpellId == 0) {
            if (ClientManaState.circle < 5) {
                return;
            }
            revert = !ClientManaState.revertSpear;
            ClientManaState.revertSpear = revert;
        } else {
            if (ClientManaState.circle < 6) {
                return;
            }
            revert = !ClientManaState.revertDarkOrb;
            ClientManaState.revertDarkOrb = revert;
        }
        FriendlyByteBuf buf = FriendlyByteBufs.create();
        buf.writeInt(baseSpellId);
        buf.writeBoolean(revert);
        ClientPackets.send(ModNetworking.SELECT_FORM, buf);
    }
}
