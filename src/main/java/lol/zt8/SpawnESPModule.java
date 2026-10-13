package lol.zt8;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.events.client.EventUpdate;
import org.rusherhack.client.api.events.render.EventRender2D;
import org.rusherhack.client.api.events.render.EventRender3D;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.render.IRenderer2D;
import org.rusherhack.client.api.render.IRenderer3D;
import org.rusherhack.client.api.render.font.IFontRenderer;
import org.rusherhack.client.api.setting.BindSetting;
import org.rusherhack.client.api.setting.ColorSetting;
import org.rusherhack.client.api.utils.ChatUtils;
import org.rusherhack.client.api.utils.WorldUtils;
import org.rusherhack.core.bind.key.NullKey;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.setting.NumberSetting;
import org.rusherhack.core.setting.StringSetting;
import org.rusherhack.core.utils.ColorUtils;

import java.util.ArrayList;
import java.util.List;
import java.awt.Color;

/**
 * Example rusherhack module
 *
 * @author John200410
 */
public class SpawnESPModule extends ToggleableModule {
	
	/**
	 * Settings
	 */
	private final BooleanSetting exampleBoolean = new BooleanSetting("Boolean", "Settings can optionally have a description", true);
	
	private final NumberSetting<Double> exampleDouble = new NumberSetting<>("Double", 0.0, -10.0, 10.0)
			
			//specifies incremental step for precise numbers
			.incremental(0.1)
			
			//predicate that determines conditions for the setting to be visible in the clickgui
			.setVisibility(this.exampleBoolean::getValue)
			
			//consumer that is called when the setting is changed
			.onChange(d -> ChatUtils.print("Changed double to " + d));
	
	private final ColorSetting exampleColor = new ColorSetting("Color", Color.CYAN)
			
			//set whether alpha is enabled in the color picker
			.setAlphaAllowed(false)
			
			//sync the color with the theme color
			.setThemeSync(true);
	
	private final StringSetting exampleString = new StringSetting("String", "Hello World!")
			
			//disables the rendering of the setting name in the clickgui
			.setNameVisible(false);
	
	private final BindSetting rotate = new BindSetting("RotateBind", NullKey.INSTANCE /* unbound */);
	private final NumberSetting<Float> rotateYaw = new NumberSetting<>("Yaw", 0f, 0f, 360f).incremental(0.1f);
	private final NumberSetting<Float> rotatePitch = new NumberSetting<>("Pitch", 0f, -90f, 90f).incremental(0.1f);

	private final NumberSetting<Integer> range = new NumberSetting<>("Range", 0, 0, 100);
	private final NumberSetting<Integer> lightLimit = new NumberSetting<>("Light Level", 0, 0, 15);
	private final NumberSetting<Integer> checkAbove = new NumberSetting<>("Air Above", 3, 1, 3);
	private final NumberSetting<Integer> sampleRate = new NumberSetting<>("Sample Rate", 5, 1, 40);
	private int tick = 1;

	private List<BlockPos> toRender = new ArrayList<BlockPos>();
	
	/**
	 * Constructor
	 */
	public SpawnESPModule() {
		super("SpawnESP", "Shows possible spawn areas", ModuleCategory.RENDER);
		
		//subsettings
		//this.rotate.addSubSettings(this.rotateYaw, this.rotatePitch);
		
		//register settings
		/*this.registerSettings(
				this.exampleBoolean,
				this.exampleDouble,
				this.exampleColor,
				this.exampleString,
				this.rotate
		);
		 */
		this.registerSettings(
				this.range,
				this.lightLimit,
				this.checkAbove,
				this.sampleRate
		);
	}

	private boolean isSpawnableBlock(BlockState state, BlockPos pos) {
		Block block = state.getBlock();
		if (
				block == Blocks.BEDROCK
						|| block == Blocks.BARRIER
						|| block instanceof TransparentBlock
						|| block instanceof ScaffoldingBlock
		) { return false; }
		if (block == Blocks.SOUL_SAND || block == Blocks.MUD) return true;
		if (block instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.TOP) return true;
		if (block instanceof StairBlock && state.getValue(StairBlock.HALF) == Half.TOP) return true;
		return state.isSolidRender(mc.level, pos);
	}

	private boolean isValidSpawn(BlockPos pos, int lightLimit, int verticalAir) {
		BlockState state = mc.level.getBlockState(pos);

		// Filter air below
		BlockState below = mc.level.getBlockState(pos.below());
		if (below.isAir()) {
			return false;
		}

		// Filter non-air, but not snow layers eaqual to 1
		if (!state.isAir()) {
			if (!(state.getBlock() instanceof SnowLayerBlock && state.getValue(SnowLayerBlock.LAYERS) == 1)) {
				return false;
			}
		}

		// Filter light levels
		if (mc.level.getBrightness(LightLayer.BLOCK, pos) > lightLimit) { return false; }

		// Filter non-air above
		for (int i = 0; i < verticalAir; i++) {
			//this.logger.info(mc.level.getBlockState(pos.above(i)).toString());
			if (!mc.level.getBlockState(pos.above(i)).isAir()) {
				return false;
			}
		}

		// Filter non-spawnable block types
		if (!isSpawnableBlock(below, pos.below())) { return false; }

		return true;
	}

	@Subscribe
	private void onUpdate(EventUpdate event) {
		tick += 1;
		if (tick % sampleRate.getValue() != 0) {
			return;
		}
		List<BlockPos> unfilteredBlocks = WorldUtils.getSphere(mc.player.blockPosition(), this.range.getValue());
		toRender.clear();
		for(BlockPos pos : unfilteredBlocks) {
			if (isValidSpawn(pos, this.lightLimit.getValue(), this.checkAbove.getValue())) {
				toRender.add(pos);
			} else {
				toRender.remove(pos);
			}
		}
	}

	//3d renderer demo
	@Subscribe
	private void onRender3D(EventRender3D event) {
		final IRenderer3D renderer = event.getRenderer();
		renderer.setDepthTest(true);

		final int color = ColorUtils.transparency(Color.RED.getRGB(), 0.5f); //fill colors look better when the alpha is not 100%
		
		//begin renderer
		renderer.begin(event.getMatrixStack());

		for (BlockPos pos : this.toRender) {
			renderer.drawPlane(pos.getX(), pos.getY(), pos.getZ(), 1, 1, Direction.UP, true, false, color);
		}
		//highlight targets

		//renderer.drawBox(entity, event.getPartialTicks(), true, true, color);
		//end renderer
		renderer.end();
	}
	
	@Override
	public void onEnable() {
		this.tick = 0;
	}
	
	@Override
	public void onDisable() {
		toRender.clear();
	}
}
