package org.evocraft.evoessentials.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evocore.data.TranslationManager;
import org.evocraft.evoessentials.EvoEssentials;

@Mod.EventBusSubscriber(modid = EvoEssentials.MODID)
public class EvoCoreCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // 1. Înregistrăm comanda de bază /evo
        dispatcher.register(Commands.literal("evo")
                .requires(s -> s.hasPermission(2)) // Necesită OP
                .then(Commands.literal("reload").executes(ctx -> {
                    TranslationManager.reload();
                    ctx.getSource().sendSuccess(() -> Component.literal("§a[EvoCore] Core message configuration was reloaded!"), true);
                    return 1;
                }))
        );
    }
}
