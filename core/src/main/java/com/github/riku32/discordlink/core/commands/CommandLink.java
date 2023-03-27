package com.github.riku32.discordlink.core.commands;

import com.github.riku32.discordlink.core.Constants;
import com.github.riku32.discordlink.core.bot.Bot;
import com.github.riku32.discordlink.core.config.Config;
import com.github.riku32.discordlink.core.database.PlayerInfo;
import com.github.riku32.discordlink.core.database.Verification;
import com.github.riku32.discordlink.core.database.enums.VerificationType;
import com.github.riku32.discordlink.core.framework.command.CommandSender;
import com.github.riku32.discordlink.core.framework.command.annotation.Command;
import com.github.riku32.discordlink.core.framework.command.annotation.Default;
import com.github.riku32.discordlink.core.framework.dependency.annotation.Dependency;
import com.github.riku32.discordlink.core.locale.DiscordLocaleAPI;
import com.github.riku32.discordlink.core.util.MojangAPI;
import com.github.riku32.discordlink.core.util.skinrenderer.RenderType;
import com.github.riku32.discordlink.core.util.skinrenderer.SkinRenderer;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;

import java.util.Locale;
import java.util.Optional;

@Command(aliases = {"link"}, userOnly = true)
public class CommandLink {
    @Dependency
    private Bot bot;

    @Dependency
    private Config config;

    @Dependency
    private MojangAPI mojangAPI;

    @Dependency
    private SkinRenderer renderContext;

    @Default
    private boolean link(CommandSender sender, Locale locale, String tag) {
        // Check if user is linked
        Optional<PlayerInfo> playerInfoOptional = PlayerInfo.find.byUuidOptional(sender.getPlayer().getUuid());
        if (playerInfoOptional.isPresent()) {
            PlayerInfo playerInfo = playerInfoOptional.get();

            bot.getJda().retrieveUserById(playerInfo.discordId).queue(user -> {
                if (playerInfo.verified) {
                    sender.sendMessage((config.isAllowUnlink() ?
                            DiscordLocaleAPI.getMessage(locale ,"link_already_linked")
                            : DiscordLocaleAPI.getMessage(locale ,"link_already_linked_unlink")));
                } else {
                    sender.sendMessage(DiscordLocaleAPI.getMessage(locale ,"link_in_process"));
                }
            });

            return false;
        }

        Member member;
        try {
            member = bot.getGuild().getMemberByTag(tag);
        } catch (IllegalArgumentException ignored) {
            sender.sendMessage(DiscordLocaleAPI.getMessage(locale ,"link_account_invalid"));
            return false;
        }

        if (member == null) {
            sender.sendMessage(DiscordLocaleAPI.getMessage(locale ,"link_account_invalid"));
            return false;
        }

        // Check if that discord account is already linked to another minecraft account
        if (PlayerInfo.find.byDiscordIdOptional(member.getId()).isPresent()) {
            sender.sendMessage(DiscordLocaleAPI.getMessage(locale ,"link_discord_linked"));
            return false;
        }

        mojangAPI.getRenderConfiguration(sender.getUniqueId(), RenderType.HEAD)
                .thenCompose(renderConfiguration -> {
                    try {
                        return renderContext.queueRenderTask(renderConfiguration, 128, 128);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .thenAccept(image -> {
                    Message verificationMessage = new MessageBuilder()
                            .setEmbeds(new EmbedBuilder()
                                    .setColor(Constants.Colors.SUCCESS)
                                    .setTitle("Minecraft Link")
                                    .setThumbnail("attachment://head.png")
                                    .setDescription("Minecraft to Discord link initiated. Press verify to complete the account link process. If you do not want to link accounts or this was not you, press cancel." +
                                            (!config.isAllowUnlink() ? "\n\n\u26A0 **THIS CANNOT BE UNDONE**" : ""))
                                    .addField("Username", sender.getName(), true)
                                    .addField("UUID", sender.getPlayer().getUuid().toString(), true)
                                    .build())
                            .setActionRows(ActionRow.of(
                                    Button.success("link_verify", "Verify"),
                                    Button.danger("link_cancel", "Cancel")
                            ))
                            .build();

                    member.getUser().openPrivateChannel().submit()
                            .thenCompose(privateChannel -> privateChannel.sendMessage(verificationMessage)
                                    .addFile(image, "head.png")
                                    .submit())
                            .whenComplete((message, error) -> {
                                if (error != null) {
                                    sender.sendMessage(DiscordLocaleAPI.getMessage(locale ,"link_dm_disabled"));
                                    return;
                                }

                                // Create the player and a message verification in the database
                                PlayerInfo playerInfo = new PlayerInfo(sender.getUniqueId(), member.getId());
                                playerInfo.save();
                                new Verification(playerInfo, VerificationType.MESSAGE_REACTION, message.getId()).save();

                                sender.sendMessage(DiscordLocaleAPI.getMessage(locale ,"link_verify"));
                            });
                });

        return true;
    }
}
