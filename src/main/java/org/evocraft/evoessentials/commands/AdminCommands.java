package org.evocraft.evoessentials.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import org.evocraft.evocore.data.EconomyManager;
import org.evocraft.evocore.util.EvoCurrencyFormatter;
import org.evocraft.evoessentials.data.WarpManager;

import java.util.Collection;

public class AdminCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // ==========================================
        // ECONOMY COMMANDS (/eco give/take/set/item)
        // ==========================================
        dispatcher.register(Commands.literal("eco")
                .requires(source -> source.hasPermission(2)) // Doar Operatorii

                .then(Commands.literal("give")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.1))
                                        .executes(context -> {
                                            Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
                                            double amount = DoubleArgumentType.getDouble(context, "amount");
                                            if (!Double.isFinite(amount) || amount <= 0.0) {
                                                context.getSource().sendFailure(Component.literal("§cInvalid amount."));
                                                return 0;
                                            }
                                            for (ServerPlayer player : players) {
                                                EconomyManager.get().addBalance(player.getUUID(), amount);
                                                player.sendSystemMessage(Component.literal("§aYou received §e" + EvoCurrencyFormatter.formatWithCurrency(amount) + "§a!"));
                                            }
                                            context.getSource().sendSuccess(() -> Component.literal("§aAdded §e" + EvoCurrencyFormatter.formatWithCurrency(amount) + " §ato " + players.size() + " player(s)."), true);
                                            return 1;
                                        }))))

                .then(Commands.literal("take")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.1))
                                        .executes(context -> {
                                            Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
                                            double amount = DoubleArgumentType.getDouble(context, "amount");
                                            if (!Double.isFinite(amount) || amount <= 0.0) {
                                                context.getSource().sendFailure(Component.literal("§cInvalid amount."));
                                                return 0;
                                            }
                                            for (ServerPlayer player : players) {
                                                EconomyManager.get().removeBalance(player.getUUID(), amount);
                                            }
                                            context.getSource().sendSuccess(() -> Component.literal("§aRemoved §e" + EvoCurrencyFormatter.formatWithCurrency(amount) + "§a."), true);
                                            return 1;
                                        }))))

                .then(Commands.literal("set")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                                        .executes(context -> {
                                            Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
                                            double amount = DoubleArgumentType.getDouble(context, "amount");
                                            if (!Double.isFinite(amount) || amount < 0.0) {
                                                context.getSource().sendFailure(Component.literal("§cInvalid amount."));
                                                return 0;
                                            }
                                            for (ServerPlayer player : players) {
                                                EconomyManager.get().setBalance(player.getUUID(), amount);
                                            }
                                            context.getSource().sendSuccess(() -> Component.literal("§aBalance was set to §e" + EvoCurrencyFormatter.formatWithCurrency(amount) + "§a."), true);
                                            return 1;
                                        }))))

                // ==========================================
                // PHYSICAL CASH ITEM
                // ==========================================
                .then(Commands.literal("item")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                    context.getSource().getPlayerOrException();
                                    context.getSource().sendFailure(Component.literal("§cPhysical cash banknote items are not implemented yet."));
                                    return 0;
                                })))
        );

        // ==========================================
        // COMENZI WARP-URI (/evowarp create/delete)
        // ==========================================
        dispatcher.register(Commands.literal("evowarp")
                .requires(source -> source.hasPermission(2)) // Doar Staff

                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String name = StringArgumentType.getString(context, "name").trim();
                                    if (name.isEmpty() || name.length() > 48) {
                                        context.getSource().sendFailure(Component.literal("§cWarp name must be between 1 and 48 characters."));
                                        return 0;
                                    }
                                    ItemStack icon = player.getMainHandItem();

                                    if (icon.isEmpty()) {
                                        player.sendSystemMessage(Component.literal("§cYou must hold an item in your hand to set it as the warp icon!"));
                                        return 0;
                                    }

                                    WarpManager.get().createWarp(name, player, icon);
                                    player.sendSystemMessage(Component.literal("§a✔ Warp §l" + name + " §awas created at your exact location!"));
                                    return 1;
                                })))

                .then(Commands.literal("delete")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        WarpManager.get().getWarpsSnapshot().keySet(), builder))
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "name").trim();
                                    if (!WarpManager.get().deleteWarp(name)) {
                                        context.getSource().sendFailure(Component.literal("§c✖ Warp '" + name + "' does not exist!"));
                                        return 0;
                                    }
                                    context.getSource().sendSuccess(() -> Component.literal("§c✔ Warp " + name + " was deleted!"), true);
                                    return 1;
                                })))
        );
    }
}
