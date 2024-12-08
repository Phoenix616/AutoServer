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

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;

public class WebServer {
	private ServerSocket serverSocket;

	public WebServer(String address, int port) {
		try {
			if (address.isEmpty()) {
				serverSocket = new ServerSocket(port); // Listen on all interfaces
			} else {
				serverSocket = new ServerSocket(port, 50, InetAddress.getByName(address));
			}
			System.out.println("Server started on " + (address.isEmpty() ? "0.0.0.0" : address) + ":" + port);
			handleRequests();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void handleRequests() {
		try {
			System.out.println("Waiting for connections...");
			Socket clientSocket = serverSocket.accept();
			System.out.println("Connection from " + clientSocket.getInetAddress().getHostAddress());
			OutputStream output = clientSocket.getOutputStream();
			String response = "HTTP/1.1 200 OK\r\n\r\n";
			output.write(response.getBytes());
			output.flush();
			clientSocket.close();
			serverSocket.close();
			System.out.println("Shutting down...");
			System.exit(0); // Shutdown the program
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
