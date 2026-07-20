package bearmetalcarts;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;

/**
 * {@code /bearmetalcarts <targets> maxspeed [<speed>]} (alias {@code /bmc}): reads or writes the
 * {@code BearMetalCarts} custom data on minecart entities, much like /data but scoped to this mod's keys.
 * Currently only {@code max_speed}; future cart behaviors hang off the same compound. All feedback is literal
 * text rather than translatable components, because vanilla clients connecting to a modded server don't have
 * this mod's lang file.
 */
public final class ModCommands {
	private static final SimpleCommandExceptionType ERROR_NO_MINECARTS = new SimpleCommandExceptionType(
			Component.literal("No minecarts matched the selector"));

	private ModCommands() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralCommandNode<CommandSourceStack> command = dispatcher.register(
				Commands.literal("bearmetalcarts")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("targets", EntityArgument.entities())
								.then(Commands.literal("maxspeed")
										.executes(c -> getMaxSpeed(c.getSource(), EntityArgument.getEntities(c, "targets")))
										.then(Commands.argument("speed", DoubleArgumentType.doubleArg(0.0D, 1024.0D))
												.executes(c -> setMaxSpeed(
														c.getSource(),
														EntityArgument.getEntities(c, "targets"),
														DoubleArgumentType.getDouble(c, "speed")))))));

		dispatcher.register(Commands.literal("bmc")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.redirect(command));
	}

	private static List<AbstractMinecart> minecarts(Collection<? extends Entity> targets) throws CommandSyntaxException {
		List<AbstractMinecart> carts = targets.stream()
				.filter(AbstractMinecart.class::isInstance)
				.map(AbstractMinecart.class::cast)
				.toList();
		if (carts.isEmpty()) {
			throw ERROR_NO_MINECARTS.create();
		}

		return carts;
	}

	private static int setMaxSpeed(CommandSourceStack source, Collection<? extends Entity> targets, double speed) throws CommandSyntaxException {
		List<AbstractMinecart> carts = minecarts(targets);
		for (AbstractMinecart cart : carts) {
			((CustomDataHolderMinecart) cart).bearmetalcarts$getCustomData().putDouble(BearMetalCartsData.TAG_MAX_SPEED, speed);
		}

		Component message = carts.size() == 1
				? Component.literal("Set max speed of ").append(carts.getFirst().getDisplayName()).append(" to " + speed)
				: Component.literal("Set max speed of " + carts.size() + " minecarts to " + speed);
		source.sendSuccess(() -> message, true);
		return carts.size();
	}

	private static int getMaxSpeed(CommandSourceStack source, Collection<? extends Entity> targets) throws CommandSyntaxException {
		List<AbstractMinecart> carts = minecarts(targets);
		for (AbstractMinecart cart : carts) {
			Optional<Double> speed = ((CustomDataHolderMinecart) cart).bearmetalcarts$getCustomData()
					.getDouble(BearMetalCartsData.TAG_MAX_SPEED);
			Component message = Component.empty()
					.append(cart.getDisplayName())
					.append(speed.map(value -> " max speed: " + value).orElse(" max speed: not set (vanilla default)"));
			source.sendSuccess(() -> message, false);
		}

		return carts.size();
	}
}
