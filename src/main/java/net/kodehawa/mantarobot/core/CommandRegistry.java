/*
 * Copyright (C) 2016-2017 David Alejandro Rubio Escares / Kodehawa
 *
 * Mantaro is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Mantaro is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Mantaro.  If not, see http://www.gnu.org/licenses/
 */

package net.kodehawa.mantarobot.core;

import com.google.common.base.Preconditions;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.kodehawa.mantarobot.MantaroBot;
import net.kodehawa.mantarobot.commands.info.stats.manager.CategoryStatsManager;
import net.kodehawa.mantarobot.commands.info.stats.manager.CommandStatsManager;
import net.kodehawa.mantarobot.core.modules.commands.AliasCommand;
import net.kodehawa.mantarobot.core.modules.commands.SimpleTreeCommand;
import net.kodehawa.mantarobot.core.modules.commands.SubCommand;
import net.kodehawa.mantarobot.core.modules.commands.TreeCommand;
import net.kodehawa.mantarobot.core.modules.commands.base.Category;
import net.kodehawa.mantarobot.core.modules.commands.base.Command;
import net.kodehawa.mantarobot.core.modules.commands.base.CommandPermission;
import net.kodehawa.mantarobot.data.Config;
import net.kodehawa.mantarobot.data.MantaroData;
import net.kodehawa.mantarobot.db.entities.DBGuild;
import net.kodehawa.mantarobot.db.entities.helpers.GuildData;
import net.kodehawa.mantarobot.utils.commands.EmoteReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandRegistry {

    private final Map<String, Command> commands;
    private final Config conf = MantaroData.config().get();

    public CommandRegistry(Map<String, Command> commands) {
        this.commands = Preconditions.checkNotNull(commands);
    }

    public CommandRegistry() {
        this(new HashMap<>());
    }

    public Map<String, Command> commands() {
        return commands;
    }

    public boolean process(GuildMessageReceivedEvent event, String cmdname, String content) {
        long start = System.currentTimeMillis();
        Command cmd = commands.get(cmdname);

        if(cmd == null) return false;

        if(MantaroData.db().getMantaroData().getBlackListedUsers().contains(event.getAuthor().getId())) {
            return false;
        }

        DBGuild dbg = MantaroData.db().getGuild(event.getGuild());
        GuildData data = dbg.getData();

        if(data.getDisabledCommands().contains(cmdname)) {
            return false;
        }

        List<String> disabledCommands = data.getChannelSpecificDisabledCommands().get(event.getChannel().getId());
        if(disabledCommands != null && disabledCommands.contains(cmdname)) {
            return false;
        }

        if(data.getDisabledUsers().contains(event.getAuthor().getId()) && !isAdmin(event.getMember())) {
            return false;
        }

        if(data.getDisabledChannels().contains(event.getChannel().getId()) && cmd.category() != Category.MODERATION) {
            return false;
        }

        if(conf.isPremiumBot() && cmd.category() == Category.CURRENCY) {
            return false;
        }

        if(data.getDisabledCategories().contains(cmd.category())) {
            return false;
        }

        if(data.getChannelSpecificDisabledCategories().computeIfAbsent(event.getChannel().getId(), wew -> new ArrayList<>()).contains(cmd.category())) {
            return false;
        }

        if(!data.getDisabledRoles().isEmpty() && event.getMember().getRoles().stream().anyMatch(r -> data.getDisabledRoles().contains(r.getId())) && !isAdmin(event.getMember())) {
            return false;
        }

        //If we are in the patreon bot, deny all requests from unknown guilds.
        if(conf.isPremiumBot() && !conf.isOwner(event.getAuthor()) && !dbg.isPremium()) {
            event.getChannel().sendMessage(EmoteReference.ERROR + "Seems like you're trying to use the Patreon bot when this guild is **not** marked as premium. " +
                    "**If you think this is an error please contact Kodehawa#3457 or poke me on #donators in the support guild**").queue();
            return false;
        }

        if(!cmd.permission().test(event.getMember())) {
            event.getChannel().sendMessage(EmoteReference.STOP + "You have no permissions to trigger this command :(").queue();
            return false;
        }

        long end = System.currentTimeMillis();
        MantaroBot.getInstance().getStatsClient().increment("commands");
        cmd.run(event, cmdname, content);

        if(cmd.category() != null && cmd.category().name() != null && !cmd.category().name().isEmpty()) {
            MantaroBot.getInstance().getStatsClient().increment("command_" + cmdname);
            MantaroBot.getInstance().getStatsClient().increment("category_" + cmd.category().name().toLowerCase());
            CommandStatsManager.log(cmdname);
            CategoryStatsManager.log(cmd.category().name().toLowerCase());
        }

        MantaroBot.getInstance().getStatsClient().histogram("command_process_time", (end - start));

        return true;
    }

    public Command register(String name, Command command) {
        commands.putIfAbsent(name, command);
        return command;
    }

    public void registerAlias(String command, String alias) {
        if(!commands.containsKey(command)) {
            System.out.println(command + " isn't in the command map...");
        }

        register(alias, new AliasCommand(alias, commands.get(command)));
    }

    public void addSubCommandTo(TreeCommand command, String name, SubCommand subCommand) {
        command.addSubCommand(name, subCommand);
    }

    public void addSubCommandTo(SimpleTreeCommand command, String name, SubCommand subCommand) {
        command.addSubCommand(name, subCommand);
    }

    private boolean isAdmin(Member member) {
        return CommandPermission.ADMIN.test(member);
    }
}
