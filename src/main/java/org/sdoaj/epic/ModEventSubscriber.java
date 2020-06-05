package org.sdoaj.epic;

import com.mojang.realmsclient.gui.ChatFormatting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.*;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mod.EventBusSubscriber(modid = Main.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModEventSubscriber {
    private static HashMap<UUID, BlockPos> playerCompassPositions = new HashMap<>();
    private static final ArrayList<CompassItem> compassesWithTracking = new ArrayList<>();

    private static final String usernameToTrack = "Dev";

    @SubscribeEvent
    public static void onRightClickWithCompass(PlayerInteractEvent event) {
        if (event.getItemStack().getItem() != Items.COMPASS) {
            return;
        }

        World world = event.getWorld();
        PlayerEntity playerToTrack = null;

        for (PlayerEntity player : world.getPlayers()) {
            if (player.getName().getString().equals(usernameToTrack)) {
                playerToTrack = player;
            }
        }

        if (playerToTrack == null) {
            return;
        }

        PlayerEntity playerWithCompass = event.getPlayer();

        if (playerWithCompass.world.getDimension().getType().getId() != 0 || playerToTrack.world.getDimension().getType().getId() != 0) {
            return;
        }

        playerCompassPositions.put(playerWithCompass.getUniqueID(), playerToTrack.getPosition());
        compassesWithTracking.clear();

        playerWithCompass.sendStatusMessage(new StringTextComponent(ChatFormatting.RED + "Tracking position updated"), true);
    }

    @SubscribeEvent
    public static void onCompassExist(TickEvent.PlayerTickEvent event) {
        PlayerEntity player = event.player;

        ArrayList<ItemStack> compasses = new ArrayList<>();
        PlayerInventory inventory = player.inventory;
        List<ItemStack> allInventories = Stream.of(inventory.mainInventory, inventory.armorInventory, inventory.offHandInventory).flatMap(NonNullList::stream).collect(Collectors.toList());

        for (ItemStack stack : allInventories) {
            if (stack.getItem() == Items.COMPASS) {
                compasses.add(stack);
            }
        }

        if (compasses.isEmpty()) {
            return;
        }

        if (!playerCompassPositions.containsKey(player.getUniqueID())) {
            playerCompassPositions.put(player.getUniqueID(), new BlockPos(0, 0, 0));
        }

        for (ItemStack stack : compasses) {
            Item item = stack.getItem();
            CompassItem compass = (CompassItem) item;

            if (compassesWithTracking.contains(compass)) {
                continue;
            }

            compassesWithTracking.add(compass);

            // mostly yoinked from CompassItem class
            compass.addPropertyOverride(new ResourceLocation("angle"), new IItemPropertyGetter() {
                @OnlyIn(Dist.CLIENT)
                private double rotation;
                @OnlyIn(Dist.CLIENT)
                private double rota;
                @OnlyIn(Dist.CLIENT)
                private long lastUpdateTick;

                @OnlyIn(Dist.CLIENT)
                private final BlockPos trackingPos = new BlockPos(playerCompassPositions.get(player.getUniqueID()));

                @OnlyIn(Dist.CLIENT)
                public float call(ItemStack stack, @Nullable World world, @Nullable LivingEntity livingEntity) {
                    if (livingEntity == null && !stack.isOnItemFrame()) {
                        return 0.0F;
                    } else {
                        boolean flag = livingEntity != null;
                        Entity entity = flag ? livingEntity : stack.getItemFrame();
                        if (world == null) {
                            world = entity.world;
                        }

                        double d0;
                        if (world.dimension.isSurfaceWorld()) {
                            double d1 = flag ? (double) entity.rotationYaw : this.getFrameRotation((ItemFrameEntity) entity);
                            d1 = MathHelper.positiveModulo(d1 / 360.0D, 1.0D);
                            double d2 = this.getAngleToTrackedPlayer(entity) / (double) ((float) Math.PI * 2F);
                            d0 = 0.5D - (d1 - 0.25D - d2);
                        } else {
                            d0 = Math.random();
                        }

                        if (flag) {
                            d0 = this.wobble(world, d0);
                        }

                        return MathHelper.positiveModulo((float) d0, 1.0F);
                    }
                }

                @OnlyIn(Dist.CLIENT)
                private double wobble(World world, double value) {
                    if (world.getGameTime() != this.lastUpdateTick) {
                        this.lastUpdateTick = world.getGameTime();
                        double d0 = value - this.rotation;
                        d0 = MathHelper.positiveModulo(d0 + 0.5D, 1.0D) - 0.5D;
                        this.rota += d0 * 0.1D;
                        this.rota *= 0.8D;
                        this.rotation = MathHelper.positiveModulo(this.rotation + this.rota, 1.0D);
                    }

                    return this.rotation;
                }

                @OnlyIn(Dist.CLIENT)
                private double getFrameRotation(ItemFrameEntity entity) {
                    return MathHelper.wrapDegrees(180 + entity.getHorizontalFacing().getHorizontalIndex() * 90);
                }

                @OnlyIn(Dist.CLIENT)
                private double getAngleToTrackedPlayer(Entity entity) {
                    return Math.atan2((double) trackingPos.getZ() - entity.getPosZ(), (double) trackingPos.getX() - entity.getPosX());
                }
            });
        }
    }
}
