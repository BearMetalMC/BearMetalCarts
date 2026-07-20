package bearmetalcarts.mixin;

import bearmetalcarts.AttributeHolderMinecart;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.commands.AttributeCommand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * DORMANT: removed from bearmetalcarts.mixins.json along with the rest of the attribute-based implementation
 * (see {@link AttributeHolderMinecart}) so vanilla clients can connect; cart speed is now edited via
 * /bearmetalcarts (bearmetalcarts.ModCommands) instead. Kept for a future BMC+ variant with a client-side mod.
 *
 * <p>Vanilla's /attribute command resolves every target through a private static
 * method typed to return LivingEntity, so anything that isn't one is rejected
 * with "%s is not a valid entity for this command" before our attribute is
 * ever looked up. This widens target resolution to also accept minecarts
 * carrying an {@link AttributeHolderMinecart} attribute map.
 */
@Mixin(AttributeCommand.class)
public abstract class AttributeCommandMixin {

	@Shadow
	@Final
	private static DynamicCommandExceptionType ERROR_NOT_LIVING_ENTITY;

	@Shadow
	@Final
	private static Dynamic2CommandExceptionType ERROR_NO_SUCH_ATTRIBUTE;

	@Shadow
	@Final
	private static Dynamic3CommandExceptionType ERROR_NO_SUCH_MODIFIER;

	@Shadow
	private static Component getAttributeDescription(Holder<Attribute> attribute) {
		throw new AssertionError();
	}

	@Unique
	private static @Nullable AttributeMap bearmetalcarts$getAttributeMap(final Entity target) {
		if (target instanceof LivingEntity livingEntity) {
			return livingEntity.getAttributes();
		}

		if (target instanceof AttributeHolderMinecart minecart) {
			return minecart.bearmetalcarts$getAttributeMap();
		}

		return null;
	}

	/**
	 * Every other command branch (base set, modifier add/remove, modifier list)
	 * only ever needs an AttributeInstance, so generalizing this one choke point
	 * is enough to make them all work for minecarts too.
	 */
	@Overwrite
	private static AttributeInstance getAttributeInstance(final Entity target, final Holder<Attribute> attribute) throws CommandSyntaxException {
		AttributeMap map = bearmetalcarts$getAttributeMap(target);
		if (map == null) {
			throw ERROR_NOT_LIVING_ENTITY.create(target.getName());
		}

		AttributeInstance instance = map.getInstance(attribute);
		if (instance == null) {
			throw ERROR_NO_SUCH_ATTRIBUTE.create(target.getName(), getAttributeDescription(attribute));
		}

		return instance;
	}

	@Overwrite
	private static int getAttributeValue(final CommandSourceStack source, final Entity target, final Holder<Attribute> attribute, final double scale) throws CommandSyntaxException {
		AttributeInstance instance = getAttributeInstance(target, attribute);
		double result = instance.getValue();
		source.sendSuccess(() -> Component.translatable("commands.attribute.value.get.success", getAttributeDescription(attribute), target.getName(), result), false);
		return (int)(result * scale);
	}

	@Overwrite
	private static int getAttributeBase(final CommandSourceStack source, final Entity target, final Holder<Attribute> attribute, final double scale) throws CommandSyntaxException {
		AttributeInstance instance = getAttributeInstance(target, attribute);
		double result = instance.getBaseValue();
		source.sendSuccess(() -> Component.translatable("commands.attribute.base_value.get.success", getAttributeDescription(attribute), target.getName(), result), false);
		return (int)(result * scale);
	}

	@Overwrite
	private static int getAttributeModifier(
			final CommandSourceStack source, final Entity target, final Holder<Attribute> attribute, final Identifier id, final double scale
	) throws CommandSyntaxException {
		AttributeInstance instance = getAttributeInstance(target, attribute);
		AttributeModifier modifier = instance.getModifier(id);
		if (modifier == null) {
			throw ERROR_NO_SUCH_MODIFIER.create(target.getName(), getAttributeDescription(attribute), id);
		}

		double result = modifier.amount();
		source.sendSuccess(
				() -> Component.translatable(
						"commands.attribute.modifier.value.get.success", Component.translationArg(id), getAttributeDescription(attribute), target.getName(), result
				),
				false
		);
		return (int)(result * scale);
	}

	@Overwrite
	private static int resetAttributeBase(final CommandSourceStack source, final Entity target, final Holder<Attribute> attribute) throws CommandSyntaxException {
		AttributeMap map = bearmetalcarts$getAttributeMap(target);
		if (map == null) {
			throw ERROR_NOT_LIVING_ENTITY.create(target.getName());
		}

		if (!map.resetBaseValue(attribute)) {
			throw ERROR_NO_SUCH_ATTRIBUTE.create(target.getName(), getAttributeDescription(attribute));
		}

		double value = map.getBaseValue(attribute);
		source.sendSuccess(
				() -> Component.translatable("commands.attribute.base_value.reset.success", getAttributeDescription(attribute), target.getName(), value), false
		);
		return 1;
	}
}
