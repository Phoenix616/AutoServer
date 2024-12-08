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

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
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
			if (server.ping().join() != null) {
				player.createConnectionRequest(server).fireAndForget();
				cancelServerTask(playerId);
			} else {
				player.showTitle(Title.title(
						getTranslation(player, "server-starting.title"),
						getTranslation(player, "server-starting.subtitle"),
						Title.Times.times(Duration.ZERO, Duration.ofSeconds(5), Duration.ZERO)
				));
			}
		}).delay(1, TimeUnit.SECONDS).repeat(5, TimeUnit.SECONDS).schedule());

		// Send a TCP packet to the server to start it
		// This is a custom implementation and not part of the velocity api
		try {
			URL url = new URL("http://" + server.getServerInfo().getAddress());
			// Send web request to url
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			connection.connect();
			if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
				log(Level.INFO, "Sent start request to " + server.getServerInfo().getAddress());
			} else {
				log(Level.WARNING, "Failed to send start request to " + server.getServerInfo().getAddress() + " (HTTP " + connection.getResponseCode() + ")");
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
}
