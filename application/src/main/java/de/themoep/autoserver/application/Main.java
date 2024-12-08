package de.themoep.autoserver.application;

/*
 * AutoServer - application
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

import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class Main {
	public static void main(String[] args) throws IOException {
		System.out.println("AutoServer is starting...");

		System.out.println("License: AGPL-3.0 (https://phoenix616.dev/licenses/agpl-v3.txt)");
		System.out.println("Source: https://github.com/Phoenix616/AutoServer");

		Properties properties = new Properties();
		properties.load(new FileReader("server.properties"));

		String address;
		int port;
		if (args.length > 1) {
			address = args[0];
			port = Integer.parseInt(args[1]);
		} else {
			address = properties.getProperty("server-ip");
			port = Integer.parseInt(properties.getProperty("server-port"));
		}

		// Start new web server on that address and port
		WebServer webServer = new WebServer(address, port);
	}
}