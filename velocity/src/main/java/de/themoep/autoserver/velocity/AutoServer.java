package de.themoep.autoserver.velocity;

/*
 * AutoServer - velocity
 * Copyright (c) 2024 Max Lee aka Phoenix616 (max@themoep.de)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import de.themoep.minedown.adventure.MineDown;
import de.themoep.utils.lang.LangLogger;
import de.themoep.utils.lang.velocity.LanguageManager;
import de.themoep.utils.lang.velocity.Languaged;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class AutoServer implements Languaged {

	@Inject
	private ProxyServer proxy;
	@Inject
	private Logger slf4jLogger;
	@Inject
	@DataDirectory
	private Path dataFolder;

	private VelocityPluginLogger pluginLogger;
	private PluginConfig config;
	private LanguageManager langManager;

	private int pingTimeout;
	private int pingInterval;

	private final Cache<String, Boolean> startingServers = CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMinutes(2)).build();
	private final Map<UUID, ScheduledTask> serverStartTasks = new HashMap<>();

	@Subscribe
	public void onProxyInitialization(ProxyInitializeEvent event) {
		pluginLogger = new VelocityPluginLogger(slf4jLogger);
		loadConfig();

		proxy.getCommandManager().register("autoserver", new AutoServerCommand(this));

		proxy.getEventManager().register(this, new EventListener(this));

		log(Level.INFO, "License: AGPL-3.0 (https://phoenix616.dev/licenses/agpl-v3.txt)");
		log(Level.INFO, "Source: https://github.com/Phoenix616/AutoServer");
	}

	boolean loadConfig() {
		log(Level.INFO, "Loading configuration...");
		config = new PluginConfig(this, dataFolder.resolve("config.yml"));

		try {
			config.createDefaultConfig();
		} catch (IOException e) {
			log(Level.SEVERE, "Unable to create default configuration file!", e);
			return false;
		}

		if (!config.load()) {
			return false;
		}

		langManager = new LanguageManager(this, config.getString("defaultLanguage"));

		pingTimeout = config.getInt("pingTimeout");
		if (pingTimeout < 1) {
			log(Level.WARNING, "Invalid pingTimeout value in config! Using default value of 3 seconds.");
			pingTimeout = 3;
		}
		pingInterval = config.getInt("pingInterval");
		if (pingInterval < 1) {
			log(Level.WARNING, "Invalid pingInterval value in config! Using default value of 10 seconds.");
			pingInterval = 10;
		}

		return true;
	}

	public ProxyServer getProxy() {
		return proxy;
	}

	@Override
	public File getDataFolder() {
		return dataFolder.toFile();
	}

	@Override
	public LangLogger getLangLogger() {
		return pluginLogger;
	}

	public void log(Level level, String message, Throwable... throwable) {
		pluginLogger.log(level, message, throwable.length > 0 ? throwable[0] : null);
	}

	public Component getTranslation(CommandSource source, String key, String... replacements) {
		return MineDown.parse(getRawTranslation(source, key), replacements);
	}

	private String getRawTranslation(CommandSource source, String key) {
		return langManager.getConfig(source).get(key);
	}

	public void startServer(Player player, RegisteredServer server) {
		serverStartTasks.computeIfAbsent(player.getUniqueId(), (playerId) -> proxy.getScheduler().buildTask(this, () -> {
			Player currentPlayer = proxy.getPlayer(playerId).orElse(null);
			if (currentPlayer == null) {
				cancelServerTask(playerId);
				return;
			}
			try {
				PingOptions pingOptions = PingOptions.builder().timeout(Duration.ofSeconds(pingTimeout)).version(currentPlayer.getProtocolVersion()).build();
				if (server.ping(pingOptions).join() != null) {
					currentPlayer.createConnectionRequest(server).connect().whenComplete((result, throwable) -> {
						if (result.isSuccessful()) {
							log(Level.INFO, "Connected player " + currentPlayer.getUsername() + " to server " + server.getServerInfo().getName());
							startingServers.invalidate(server.getServerInfo().getName());
						} else {
							log(Level.WARNING, "Failed to connect player " + currentPlayer.getUsername() + " to server " + server.getServerInfo().getName()
									+ ": " + result.getReasonComponent().map(MineDown::stringify).orElse("Unknown reason"));
						}
						if (throwable != null) {
							log(Level.SEVERE, "Failed to connect player to server " + server.getServerInfo().getName(), throwable);
						}
					});
					cancelServerTask(playerId);
					return;
				}
			} catch (Exception e) {
				// Server is offline
			}
			currentPlayer.showTitle(Title.title(
					getTranslation(currentPlayer, "server-starting.title", "server", server.getServerInfo().getName()),
					getTranslation(currentPlayer, "server-starting.subtitle", "server", server.getServerInfo().getName()),
					Title.Times.times(Duration.ZERO, Duration.ofSeconds(pingInterval + 1), Duration.ZERO)
			));
		}).delay(1, TimeUnit.SECONDS).repeat(pingInterval, TimeUnit.SECONDS).schedule());

		if (startingServers.getIfPresent(server.getServerInfo().getName()) != null) {
			// Already starting
			return;
		}
		// Send a TCP packet to the server to start it
		// This is a custom implementation and not part of the velocity api
		try {
			URL url = new URL("http://" + server.getServerInfo().getAddress().getHostString() + ":" + server.getServerInfo().getAddress().getPort() + "/start");
			// Send web request to url
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			startingServers.put(server.getServerInfo().getName(), true);
			connection.connect();
			if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
				log(Level.INFO, "Sent start request to " + url);
			} else {
				log(Level.WARNING, "Failed to send start request to " + url + " (HTTP " + connection.getResponseCode() + ")");
				startingServers.invalidate(server.getServerInfo().getName());
			}
			connection.disconnect();
		} catch (MalformedURLException e) {
			log(Level.SEVERE, "Invalid server address: " + server.getServerInfo().getAddress(), e);
		} catch (ProtocolException e) {
			log(Level.SEVERE, "TCP connection error while trying to connect to " + server.getServerInfo().getAddress(), e);
		} catch (IOException e) {
			log(Level.SEVERE, "IO error while trying to connect to " + server.getServerInfo().getAddress(), e);
		}
	}

	void cancelServerTask(UUID playerId) {
		ScheduledTask task = serverStartTasks.remove(playerId);
		if (task != null) {
			task.cancel();
		}
	}

	public int getPingTimeout() {
		return pingTimeout;
	}
}
