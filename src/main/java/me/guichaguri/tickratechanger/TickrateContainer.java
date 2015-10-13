package me.guichaguri.tickratechanger;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.relauncher.Side;
import java.util.Arrays;
import me.guichaguri.tickratechanger.TickrateMessageHandler.TickrateMessage;
import me.guichaguri.tickratechanger.api.TickrateAPI;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.GameRules;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

/**
 * @author Guilherme Chaguri
 */
public class TickrateContainer extends DummyModContainer {

    private static ModMetadata createMetadata() {
        ModMetadata meta = new ModMetadata();
        meta.modId = TickrateChanger.MODID;
        meta.name = "Tickrate Changer";
        meta.version = TickrateChanger.VERSION;
        meta.authorList = Arrays.asList("Guichaguri");
        meta.description = "Let you change the client/server tickrate";
        meta.url = "http://minecraft.curseforge.com/mc-mods/230233-tickratechanger";
        return meta;
    }

    public TickrateContainer() {
        super(createMetadata());
    }

    @Override
    public boolean registerBus(EventBus bus, LoadController controller) {
        bus.register(this);
        return true;
    }

    @Subscribe
    public void preInit(FMLPreInitializationEvent event) {
        TickrateChanger.NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel("TickrateChanger");
        TickrateChanger.NETWORK.registerMessage(TickrateMessageHandler.class, TickrateMessage.class, 0, Side.CLIENT);

        TickrateChanger.CONFIG_FILE = event.getSuggestedConfigurationFile();
        Configuration cfg = new Configuration(TickrateChanger.CONFIG_FILE);
        TickrateChanger.DEFAULT_TICKRATE = (float)cfg.get("default", "tickrate", 20.0,
                "Default tickrate. The game will always initialize with this value.").getDouble(20);
        TickrateChanger.MIN_TICKRATE = (float)cfg.get("minimum", "tickrate", 0.1,
                "Minimum tickrate from servers. Prevents really low tickrate values.").getDouble(0.1);
        TickrateChanger.MAX_TICKRATE = (float)cfg.get("maximum", "tickrate", 1000,
                "Maximum tickrate from servers. Prevents really high tickrate values.").getDouble(1000);
        TickrateChanger.SHOW_MESSAGES = cfg.get("miscellaneous", "show-messages", true,
                "If it will show log messages in the console and the game").getBoolean(true);
        cfg.save();
    }

    @Subscribe
    public void init(FMLInitializationEvent event) {
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
        TickrateAPI.changeTickrate(TickrateChanger.DEFAULT_TICKRATE);
    }

    @Subscribe
    public void start(FMLServerStartingEvent event) {
        event.registerServerCommand(new TickrateCommand());
    }

    @SubscribeEvent
    public void chat(ClientChatReceivedEvent event) {
        if(event.message instanceof ChatComponentTranslation) {
            ChatComponentTranslation t = (ChatComponentTranslation)event.message;
            if(t.getKey().equals("tickratechanger.show.clientside")) {
                event.message = new ChatComponentText("");
                event.message.appendSibling(TickrateCommand.c("Your Current Client Tickrate: ", 'f', 'l'));
                event.message.appendSibling(TickrateCommand.c(TickrateAPI.getClientTickrate() + " ticks per second", 'a'));
            }
        }
    }

    @SubscribeEvent
    public void disconnect(ClientDisconnectionFromServerEvent event) {
        TickrateAPI.changeServerTickrate(TickrateChanger.DEFAULT_TICKRATE);
        TickrateAPI.changeClientTickrate(null, TickrateChanger.DEFAULT_TICKRATE);
    }

    @SubscribeEvent
    public void connect(ClientConnectedToServerEvent event) {
        if(event.isLocal) {
            float tickrate = TickrateChanger.DEFAULT_TICKRATE;
            try {
                GameRules rules = MinecraftServer.getServer().getEntityWorld().getGameRules();
                if(rules.hasRule(TickrateChanger.GAME_RULE)) {
                    tickrate = Float.parseFloat(rules.getGameRuleStringValue(TickrateChanger.GAME_RULE));
                }
            } catch(Exception ex) {
                ex.printStackTrace();
            }
            TickrateAPI.changeServerTickrate(tickrate);
            TickrateAPI.changeClientTickrate(null, tickrate);
        } else {
            TickrateAPI.changeClientTickrate(null, 20F);
        }
    }

    @SubscribeEvent
    public void connect(PlayerLoggedInEvent event) {
        if(FMLCommonHandler.instance().getEffectiveSide() == Side.SERVER) {
            float tickrate = TickrateChanger.DEFAULT_TICKRATE;
            try {
                GameRules rules = MinecraftServer.getServer().getEntityWorld().getGameRules();
                if(rules.hasRule(TickrateChanger.GAME_RULE)) {
                    tickrate = Float.parseFloat(rules.getGameRuleStringValue(TickrateChanger.GAME_RULE));
                }
            } catch(Exception ex) {
                ex.printStackTrace();
            }
            TickrateAPI.changeClientTickrate(event.player, tickrate);
        }
    }

}
