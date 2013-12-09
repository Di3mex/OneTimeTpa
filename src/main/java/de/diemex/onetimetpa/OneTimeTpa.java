package de.diemex.onetimetpa;


import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * @author Diemex
 */
public class OneTimeTpa extends JavaPlugin implements Listener
{
    private Set<String> playersWhoUsedCommand;
    private Map<String, String> pendingRequests;
    private Map<String, Long> firstLogins;
    private int commandTimeout = 30;
    private String mainNode = "OneTimeTpa";
    private final String timeoutNode = mainNode + ".timeout in minutes after first login";
    private final String playersNode = mainNode + ".players who have used tpa";
    private final String timeNode = mainNode + ".firstlogins";
    private final String teleportPerm = "onetimetpa.teleport";


    @Override
    public void onEnable()
    {
        playersWhoUsedCommand = new HashSet<String>();
        pendingRequests = new HashMap<String, String>();
        firstLogins = new HashMap<String, Long>();

        FileConfiguration config = getConfig();

        //Set the node to the default value if it hasn't been set
        if (config.isInt(timeoutNode))
            commandTimeout = config.getInt(timeoutNode);
        else
            config.set(timeoutNode, commandTimeout);

        //Load the previous players from file if set
        if (config.isList(playersNode))
            playersWhoUsedCommand = new HashSet<String>(config.getStringList(playersNode));

        //Save the time for the players that are online if not in there already
        if (config.isList(timeNode))
        {
            String [] splitted;
            for (String playerNames : config.getStringList(timeNode))
            {
                splitted = playerNames.split("@");
                firstLogins.put(splitted[0], Long.parseLong(splitted[1]));
            }
        }

        //Put all current players in there
        long currentTime = System.currentTimeMillis();
        for (Player player : getServer().getOnlinePlayers())
            if (!firstLogins.containsKey(player.getName()))
                firstLogins.put(player.getName(), currentTime);

        saveTimes();

        saveConfig();
    }


    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
    {
        if (cmd.getName().equalsIgnoreCase("tpa"))
        {
            if (sender.hasPermission("onetimetpa.teleport"))
            {
                if (sender instanceof Player)
                {
                    //Sending of requests
                    if (isEligibleForTpa((Player) sender))
                    {
                        //actual teleportation
                        if (args.length > 0)
                        {
                            Player other = getServer().getPlayer(args[0]);
                            if (other != null)
                            {
                                //getServer().dispatchCommand(Bukkit.getConsoleSender(), "tellraw @a {\"text\":\"has requested to teleport to you. Type /tpaaccept to accept\",\"color\":\"white\",\"bold\":\"false\",\"italic\":\"false\",\"underlined\":\"false\",\"strikethrough\":\"false\",\"obfuscated\":\"false\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/tpaaccept\"},\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click here to accept\"}}");
                                other.sendMessage(ChatColor.YELLOW + sender.getName() + " has requested to teleport to you. Type /tpaccept to accept or tpdeny to deny");
                                sender.sendMessage(ChatColor.YELLOW + "A teleport request has been send to " + other.getName());
                                pendingRequests.put(other.getName(), sender.getName());
                            } else
                                sender.sendMessage(ChatColor.YELLOW + "Player with name " + args[0] + " doesn't exist!");

                        } else
                            sender.sendMessage(ChatColor.YELLOW + "You haven't supplied a player to teleport to");
                    } else
                        sender.sendMessage(ChatColor.YELLOW + "You cannot use this command anymore.");

                } else
                    sender.sendMessage(ChatColor.YELLOW + "/tpa only makes sense for players");
                return true;
            } else
                sender.sendMessage(ChatColor.RED + "You lack permission onetimetpa.teleport");

        }

        //TPAACCEPT
        else if (cmd.getName().equalsIgnoreCase("tpaccept"))
        {
            if (sender.hasPermission(teleportPerm))
            {
                if (sender instanceof Player && sender.hasPermission(teleportPerm))
                {
                    if (pendingRequests.containsKey(sender.getName()))
                    {
                        Player requester = getServer().getPlayer(pendingRequests.get(sender.getName()));
                        if (requester != null)
                        {
                            requester.teleport((Player) sender);
                            playersWhoUsedCommand.add(requester.getName());
                            savePlayersToFile();
                        } else
                            //TODO timeout for tpa
                            sender.sendMessage(ChatColor.YELLOW + "Pending request from " + pendingRequests.get(sender.getName()) + " but player can't be found. Maybe /tpdeny if you want to accept a request from someone else?");
                    } else
                        sender.sendMessage(ChatColor.YELLOW + "No pending request");
                } else
                    sender.sendMessage(ChatColor.YELLOW + "This command only makes sense for players!");
            } else
                sender.sendMessage(ChatColor.RED + "You lack permission onetimetpa.teleport");
        }
        //TPDENY
        else if (cmd.getName().equalsIgnoreCase("tpdeny"))
        {
            if (sender.hasPermission(teleportPerm))
            {
                if (pendingRequests.containsKey(sender.getName()))
                {
                    sender.sendMessage(ChatColor.YELLOW + "The pending request from " + pendingRequests.get(sender.getName()) + " has been denied");
                    Player other = getServer().getPlayer(pendingRequests.get(sender.getName()));
                    if (other != null)
                        other.sendMessage(ChatColor.YELLOW + "Your request has been denied by " + sender.getName());

                    pendingRequests.remove(sender.getName());
                } else
                    sender.sendMessage(ChatColor.YELLOW + "There are no pending requests");
            } else
                sender.sendMessage(ChatColor.RED + "You lack permission onetimetpa.teleport");
        }
        return false;
    }


    public void saveTimes()
    {
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, Long> times : firstLogins.entrySet())
        {
            out.add(times.getKey() + "@" + times.getValue());
        }
        getConfig().set(timeNode, out);
    }


    public void savePlayersToFile()
    {
        getConfig().set(playersNode, playersWhoUsedCommand);
        saveConfig();
    }


    public boolean isEligibleForTpa(Player player)
    {
        //First check their online time (in milliseconds)
        return (player.getFirstPlayed() == 0 || (getFirstLogin(player) - System.currentTimeMillis() < commandTimeout * 60 * 1000))
                && !playersWhoUsedCommand.contains(player.getName());
    }

    public long getFirstLogin(Player player)
    {
        return firstLogins.get(player.getName());
    }


    @EventHandler
    public void onLogin(PlayerLoginEvent event)
    {
        if (!firstLogins.containsKey(event.getPlayer().getName()))
        {
            firstLogins.put(event.getPlayer().getName(), System.currentTimeMillis());
            saveTimes();
        }
    }


    @Override
    public void onDisable()
    {
        //You never know with bukkits classloader
        playersWhoUsedCommand = null;
        pendingRequests = null;
        firstLogins = null;
    }
}
