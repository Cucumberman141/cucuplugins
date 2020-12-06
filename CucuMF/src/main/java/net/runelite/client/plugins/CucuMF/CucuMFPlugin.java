//Created by PluginCreator by ImNo: https://github.com/ImNoOSRS 
package net.runelite.client.plugins.CucuMF;

import com.google.inject.Provides;
import com.owain.chinbreakhandler.ChinBreakHandler;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.awt.Rectangle;
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginType;
import net.runelite.client.plugins.iutils.*;
import net.runelite.client.plugins.iutils.ObjectUtils;
import net.runelite.client.plugins.iutils.CalculationUtils;
import net.runelite.client.plugins.iutils.InventoryUtils;
import net.runelite.client.plugins.iutils.iUtils;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.plugins.iutils.InterfaceUtils;
import net.runelite.client.plugins.iutils.InventoryUtils;
import net.runelite.client.plugins.iutils.MenuUtils;
import net.runelite.client.plugins.iutils.MouseUtils;
import net.runelite.client.plugins.iutils.ObjectUtils;
import net.runelite.client.plugins.iutils.PlayerUtils;
import net.runelite.client.plugins.iutils.iUtils;


import static net.runelite.client.plugins.iutils.iUtils.iterating;
import static net.runelite.client.plugins.iutils.iUtils.sleep;
import static net.runelite.client.plugins.CucuMF.CucuMFState.*;


import net.runelite.client.ui.overlay.OverlayManager;
import org.pf4j.Extension;

@Extension
@PluginDependency(iUtils.class)
@PluginDescriptor(
		name = "Cucumber Master Farmer",
		description = "robs the mf MF",
		type = PluginType.SKILLING
)
@Slf4j
public class CucuMFPlugin extends Plugin {
	// Injects our config
	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private iUtils utils;

	@Inject
	private BankUtils bank;

	@Inject
	private MouseUtils mouse;

	@Inject
	private PlayerUtils playerUtils;

	@Inject
	private InventoryUtils inventory;

	@Inject
	private InterfaceUtils interfaceUtils;

	@Inject
	private CalculationUtils calc;

	@Inject
	private MenuUtils menu;

	@Inject
	private NPCUtils npc;

	@Inject
	private ObjectUtils obj;

	@Inject
	private WalkUtils walk;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ExecutorService executorService;

	@Inject
	private ChinBreakHandler chinBreakHandler;

	@Inject
	private CucuMFConfig config;

	@Inject
	private ClientThread clientThread;
 	@Inject
	private CucuMFOverlay overlay;

	MenuEntry targetMenu;
	GameObject targetObject;
	Player player;
	LocalPoint beforeLoc;
	NPC targetNPC;
	CucuMFState state;
	boolean startBot;
	public static long sleepLength;
	int tickLength;
	Instant botTimer;
	Rectangle clickBounds;
	int timeout;
	Set<String> foodMenu = Set.of("Eat", "Drink");



	@Provides
	CucuMFConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(CucuMFConfig.class);
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event) {
		if (event.getGroup().equals("CucuMF"))
			if (!startBot || chinBreakHandler.isBreakActive(this))
			{
				return;
			}
		player = client.getLocalPlayer();
		if (client != null && player != null && client.getGameState() == GameState.LOGGED_IN)
		{
			if (chinBreakHandler.shouldBreak(this))
			{
				chinBreakHandler.startBreak(this);
				timeout = 5;
			}

		}
	}

	private long sleepDelay()
	{
		sleepLength = calc.randomDelay(config.sleepWeightedDistribution(), config.sleepMin(), config.sleepMax(), config.sleepDeviation(), config.sleepTarget());
		return sleepLength;
	}



	private int tickDelay()
	{
		int tickLength = (int) calc.randomDelay(config.tickDelayWeightedDistribution(), config.tickDelayMin(), config.tickDelayMax(), config.tickDelayDeviation(), config.tickDelayTarget());
		log.debug("tick delay for {} ticks", tickLength);
		return tickLength;

	}


	@Override
	protected void startUp()
	{
		chinBreakHandler.registerPlugin(this);
	}

	@Override
	protected void shutDown()
	{
		resetVals();
		chinBreakHandler.unregisterPlugin(this);
	}

	private void resetVals()
	{
		log.debug("stopping gitting the poor guy");
		overlayManager.remove(overlay);
		chinBreakHandler.stopPlugin(this);
		state = null;
		startBot = false;
		//targetNPC = null;
	}

	@Subscribe
	private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked)
	{
		if (!configButtonClicked.getGroup().equalsIgnoreCase("CucuMF"))
		{
			return;
		}
		log.info("button {} pressed!", configButtonClicked.getKey());
		if (configButtonClicked.getKey().equals("startButton"))
		{
			if (!startBot)
			{
				startBot = true;
				chinBreakHandler.startPlugin(this);
				state = null;
				targetMenu = null;
				botTimer = Instant.now();
				overlayManager.add(overlay);
			}
			else
			{
				resetVals();
			}
		}
	}



	private void interactNPC()
	{
		targetNPC = npc.findNearestNpc("Master Farmer");

		if (targetNPC != null)
		{
			targetMenu = new MenuEntry("Pickpocket", "", targetNPC.getIndex(),  MenuOpcode.NPC_THIRD_OPTION.getId(), 0, 0, false);
			targetNPC.getConvexHull().getBounds();
			Rectangle rectangle = targetNPC.getConvexHull().getBounds();
			utils.doActionMsTime(targetMenu, rectangle, sleepDelay());
		}
		else
		{
			log.info("NPC is null");
		}
	}

	private void eat()
	{
		WidgetItem food = inventory.getItemMenu(foodMenu);

		if (food != null) {
			targetMenu = new MenuEntry("Eat", "", food.getId(), MenuOpcode.ITEM_FIRST_OPTION.getId(),
					food.getIndex(), WidgetInfo.INVENTORY.getId(), true);
			utils.doActionMsTime(targetMenu, food.getCanvasBounds(), sleepDelay());
			timeout = tickDelay();
		}
		else
		{
			log.info("We're out of food");
			timeout = 10;
		}
	}

	private void banking(){

		bank.withdrawItemAmount(3144, 5);
		timeout = 2 + tickDelay();

		if (inventory.containsItemAmount(3144, 5, false, false)) {
			bank.close();
			timeout = 2 + tickDelay();

		}
		log.info("getting karambwans");

	}

	private int checkHitpoints()
	{
		try{
			return client.getBoostedSkillLevel(Skill.HITPOINTS);
		} catch (Exception e) {
			return 0;
		}
	}


	private void dropping() {

		if (!bank.isOpen()) {
			inventory.dropAllExcept(utils.stringToIntList("5295, 5300, 5302, 5294, 5297, 5299, 4564, 3144, 22896, 22895, 22894, 22893, 22879, 5300, 5307, 5179, 5178, 5177, 5176, 5296, 5298, 5100, 5268, 5269, 5270, 5321, 5143, 5144, 5145, 5146"), true, config.sleepMin(), config.sleepMax());
		}
	}
	private void openBank()
	{
		targetObject = obj.findNearestBank();
		if (targetObject != null)
		{
			targetMenu = new MenuEntry("", "", targetObject.getId(),
					3, targetObject.getSceneMinLocation().getX(),
					targetObject.getSceneMinLocation().getY(), true);
			utils.doGameObjectActionMsTime(targetObject, targetMenu.getOpcode(), sleepDelay());
		}
		else
		{
			utils.sendGameMessage("Bank not found, stopping");
		}
	}

	public CucuMFState getState()
	{

		if (bank.isOpen() && !inventory.isFull() && !inventory.containsItem(ItemID.COOKED_KARAMBWAN)){
			log.info("need 2 withdrawl");
			return WITHDRAWLING;
		}

		if (foodMenu != null && checkHitpoints() < 40){
			log.info("must eat");
			return EATING;
		}

		if (timeout > 0)
		{
			playerUtils.handleRun(20, 30);
			return TIMEOUT;
		}
		if (iterating)
		{
			return ITERATING;
		}
		if (playerUtils.isMoving(beforeLoc) || player.getAnimation() == 714) //teleport animation
		{
			playerUtils.handleRun(20, 30);
			log.info("Moving");
			return MOVING;
		}

		if (!inventory.containsItem(ItemID.COOKED_KARAMBWAN) && checkHitpoints() < 60)
		{
			return BANKING;
		}

		if (!inventory.isFull())
		{
			log.info("pickpocketing the mf");
			return PICKPOCKETMF;
		}
		if (inventory.isFull() && !bank.isOpen()){
			log.info("dropping");
			return DROPPING;
		}


		if (chinBreakHandler.shouldBreak(this))
		{
			return HANDLE_BREAK;
		}

		if (!inventory.containsItem(ItemID.COOKED_KARAMBWAN) && checkHitpoints() < 60)
		{
			return BANKING;
		}

		if (bank.isOpen() && !inventory.isFull()){
			return WITHDRAWLING;
		}
		if (client.getLocalPlayer().getAnimation()!=-1){
			return ANIMATING;

		}
		return UNHANDLED_STATE;
		}

	@Subscribe
	private void onBeforeRender(final BeforeRender event) {
		if (this.client.getGameState() != GameState.LOGGED_IN) {
			return;
		}
	}


	@Subscribe
	private void onWidgetLoaded(WidgetLoaded event)
	{

	}

	@Subscribe
	private void onGameTick(GameTick event)
	{
		if (!startBot || chinBreakHandler.isBreakActive(this))
		{
			return;
		}
		targetNPC = npc.findNearestNpc("Master Farmer");

		player = client.getLocalPlayer();
		if (client != null && player != null && client.getGameState() == GameState.LOGGED_IN)
		{
			if (!client.isResized())
			{
				utils.sendGameMessage("illu - client must be set to resizable");
				startBot = false;
				return;
			}

			state = getState();
			beforeLoc = player.getLocalLocation();
			switch (state)
			{
				case MOVING:
					timeout = tickDelay();
					break;
				case ANIMATING:
				case WITHDRAWLING:
					banking();
					timeout = tickDelay();
					break;
				case BANKING:
					openBank();
					timeout = tickDelay();
					break;
				case EATING:
					eat();
					log.info("eating!");
					timeout = tickDelay();
					break;
				case PICKPOCKETMF:
					interactNPC();
					log.info("pickpocketing the mf");
					break;
				case DROPPING:
					dropping();
					timeout = tickDelay();
					break;
				case TIMEOUT:
					timeout--;
					break;
				case ITERATING:
					break;


				case HANDLE_BREAK:
					chinBreakHandler.startBreak(this);
					timeout = 10;
					break;

			}
		}
	}
}
