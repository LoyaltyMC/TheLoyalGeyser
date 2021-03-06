/*
 * Copyright (c) 2019-2020 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.connector.network.translators.bedrock;

import com.nukkitx.protocol.bedrock.packet.PlayerListPacket;
import com.nukkitx.protocol.bedrock.data.entity.EntityFlag;
import org.geysermc.connector.entity.PlayerEntity;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.network.translators.PacketTranslator;
import org.geysermc.connector.network.translators.Translator;
import org.geysermc.connector.utils.SkinUtils;

import com.nukkitx.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;

import java.util.concurrent.TimeUnit;

@Translator(packet = SetLocalPlayerAsInitializedPacket.class)
public class BedrockSetLocalPlayerAsInitializedTranslator extends PacketTranslator<SetLocalPlayerAsInitializedPacket> {
    @Override
    public void translate(SetLocalPlayerAsInitializedPacket packet, GeyserSession session) {
        if (session.getPlayerEntity().getGeyserId() == packet.getRuntimeEntityId()) {
            if (!session.getUpstream().isInitialized()) {
                session.getUpstream().setInitialized(true);

  //              PlayerListPacket playerListPacket = new PlayerListPacket();
  //              playerListPacket.setAction(PlayerListPacket.Action.ADD);
                for (PlayerEntity entity : session.getEntityCache().getEntitiesByType(PlayerEntity.class)) {
                    if (!entity.isValid()) {
                        // async skin loading
                        SkinUtils.requestAndHandleSkinAndCape(entity, session, skinAndCape -> entity.sendPlayer(session));
                    }

                    if (entity.isPlayerList()) {
                        playerListPacket.getEntries().add(SkinUtils.buildCachedEntry(session, entity));
                    }
                }

                session.getUpstream().sendPacket(playerListPacket);

                // Send Skulls
                for (PlayerEntity entity : session.getSkullCache().values()) {
                    entity.spawnEntity(session);

                    SkinUtils.requestAndHandleSkinAndCape(entity, session, (skinAndCape) -> session.getConnector().getGeneralThreadPool().schedule(() -> {
                        entity.getMetadata().getFlags().setFlag(EntityFlag.INVISIBLE, false);
                        entity.updateBedrockMetadata(session);
                    }, 2, TimeUnit.SECONDS));
                }

            }
        }
    }
}
