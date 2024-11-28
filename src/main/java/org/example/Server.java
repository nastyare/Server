package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private static final Map<String, PrintWriter> clients = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        setupLogDirectory();
        Properties properties = loadProperties();

        String host = properties.getProperty("server.host", "localhost");
        int port = Integer.parseInt(properties.getProperty("server.port", "1517"));

        logger.info("Server started on {}:{}", host, port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            logger.error("Error starting the server", e);
        }
    }

    private static void setupLogDirectory() {
        File logDirectory = new File("src/main/resources/org/example/server/logs");
        if (!logDirectory.exists() && logDirectory.mkdirs()) {
            logger.info("Log directory created successfully.");
        } else {
            logger.error("Failed to create log directory.");
        }
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream input = Server.class.getResourceAsStream("/org/example/server/server.properties")) {
            if (input == null) {
                logger.error("server.properties file not found.");
            } else {
                properties.load(input);
                logger.info("server.properties file loaded successfully.");
            }
        } catch (IOException e) {
            logger.error("Error loading server.properties file", e);
        }
        return properties;
    }

    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
            ) {
                String nickname = in.readLine();
                clients.put(nickname, out);
                logger.info("User '{}' connected to the server", nickname);

                String message;
                while ((message = in.readLine()) != null) {
                    processMessage(nickname, message, out);
                }
            } catch (IOException e) {
                logger.warn("Communication error with client: {}", e.getMessage());
            } finally {
                cleanupClient();
            }
        }

        private void processMessage(String nickname, String message, PrintWriter out) {
            if (message.equals("/users")) {
                sendUserList(out);
            } else if (message.startsWith("/w")) {
                sendPrivateMessage(nickname, message, out);
            } else {
                broadcastMessage(nickname, message);
            }
        }

        private void sendUserList(PrintWriter out) {
            out.println("Connected users: " + String.join(", ", clients.keySet()));
        }

        private void sendPrivateMessage(String sender, String message, PrintWriter out) {
            String[] parts = message.split(" ", 3);
            if (parts.length < 3) {
                out.println("Invalid format. Use: /w <nickname> <message>");
                return;
            }
            String targetNick = parts[1];
            String privateMessage = parts[2];
            PrintWriter targetOut = clients.get(targetNick);

            if (targetOut != null) {
                logger.info("Private message from '{}' to '{}': {}", sender, targetNick, privateMessage);
                targetOut.println("[Private from " + sender + "]: " + privateMessage);
            } else {
                out.println("User " + targetNick + " not found.");
            }
        }

        private void broadcastMessage(String sender, String message) {
            logger.info("Broadcast message from '{}': {}", sender, message);
            for (Map.Entry<String, PrintWriter> client : clients.entrySet()) {
                if (!client.getKey().equals(sender)) {
                    client.getValue().println("[From " + sender + "]: " + message);
                }
            }
        }

        private void cleanupClient() {
            try {
                clientSocket.close();
            } catch (IOException e) {
                logger.error("Error closing client socket", e);
            }
        }
    }
}
