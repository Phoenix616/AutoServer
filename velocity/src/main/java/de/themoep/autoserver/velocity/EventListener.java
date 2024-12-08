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

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class EventListener {
	private final AutoServer plugin;

	public EventListener(AutoServer plugin) {
		this.plugin = plugin;
	}

	@Subscribe(order = PostOrder.CUSTOM, priority = -9001)
	public void onSelectServer(PlayerChooseInitialServerEvent event) {
		if (event.getInitialServer().isEmpty() || !event.getInitialServer().get().getPlayersConnected().isEmpty()) {
			// Server is not empty therefore it's online and the player can just keep connecting to it
			return;
		}

		// Check if target server is online
		ServerPing ping = event.getInitialServer().get()
				.ping(PingOptions.builder()
						.version(event.getPlayer().getProtocolVersion())
						.timeout(10, TimeUnit.SECONDS)
						.build())
				.join();
		if (ping != null) {
			// Server is online, let the player connect
			return;
		}

		// Route player to fallback and start server
		for (String serverName : plugin.getProxy().getConfiguration().getAttemptConnectionOrder()) {
			Optional<RegisteredServer> fallbackServer = plugin.getProxy().getServer(serverName);
			if (fallbackServer.isPresent() && fallbackServer.get() != event.getInitialServer().orElse(null)) {
				event.setInitialServer(fallbackServer.get());
				break;
			}
		}

		// Trigger server start and checker
		plugin.startServer(event.getPlayer(), event.getInitialServer().get());
	}

	@Subscribe
	public void onPlayerQuit(DisconnectEvent event) {
		plugin.cancelServerTask(event.getPlayer().getUniqueId());
	}

	@Subscribe
	public void onServerSwitch(ServerConnectedEvent event) {
		if (event.getPreviousServer().isPresent()
				&& plugin.getProxy().getConfiguration().getAttemptConnectionOrder().contains(
						event.getPreviousServer().get().getServerInfo().getName())) {
			plugin.cancelServerTask(event.getPlayer().getUniqueId());
		}
	}
}
