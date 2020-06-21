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

package org.geysermc.platform.standalone;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.geysermc.connector.configuration.GeyserJacksonConfiguration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeyserStandaloneConfiguration extends GeyserJacksonConfiguration {

    @JsonProperty("floodgate-key-file")
    private String floodgateKeyFile;

    private Map<String, UserAuthenticationInfo> userAuths;

    @JsonProperty("command-suggestions")
    private boolean isCommandSuggestions;

    @JsonProperty("passthrough-motd")
    private boolean isPassthroughMotd;

    @JsonProperty("passthrough-player-counts")
    private boolean isPassthroughPlayerCounts;

    @JsonProperty("legacy-ping-passthrough")
    private boolean isLegacyPingPassthrough;

    @JsonProperty("ping-passthrough-interval")
    private int pingPassthroughInterval;

    @JsonProperty("max-players")
    private int maxPlayers;

    @JsonProperty("debug-mode")
    private boolean debugMode;

    @JsonProperty("general-thread-pool")
    private int generalThreadPool;

    @JsonProperty("allow-third-party-capes")
    private boolean allowThirdPartyCapes;

    @JsonProperty("allow-third-party-ears")
    private boolean allowThirdPartyEars;

    @JsonProperty("default-locale")
    private String defaultLocale;

    @JsonProperty("cache-chunks")
    private boolean cacheChunks;

    @JsonProperty("allow-custom-skulls")
    private boolean isAllowCustomSkulls;

    @JsonProperty("above-bedrock-nether-building")
    private boolean isAboveBedrockNetherBuilding;

    private MetricsInfo metrics;

    @Override
    public Path getFloodgateKeyFile() {
        return Paths.get(floodgateKeyFile);
    }
}
