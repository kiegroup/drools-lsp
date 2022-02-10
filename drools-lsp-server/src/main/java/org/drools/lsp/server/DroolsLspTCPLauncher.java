package org.drools.lsp.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

/**
 * TCP Launcher for hello language server. Usefull for remote connection and debug
 */
public class DroolsLspTCPLauncher {

    private static final String ADDRESS = "127.0.0.1";
    private static final int PORT = 9925;

    public static void main(String[] args) throws Exception {
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("The chat server is running on PORT " + PORT + ": " + serverSocket);
            System.out.println("wait for clients to connect");
            Socket socket = serverSocket.accept();
            System.out.println("Connected " + socket);
            startServer(socket.getInputStream(), socket.getOutputStream());
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
//        // Initialize the server
//        DroolsLspServer server = new DroolsLspServer();
//        // start the socket server
//        InetAddress inetAddress = InetAddress.getByName(ADDRESS);
//
//        ExecutorService threadPool = Executors.newCachedThreadPool();
//        try (ServerSocket serverSocket = new ServerSocket(PORT, 50)) {
//            threadPool.submit(() -> {
//                while (true) {
//                    System.out.println("The chat server is running on PORT " + PORT + ": " + serverSocket);
//                    System.out.println("wait for clients to connect");
//                    // wait for clients to connect
//                    Socket socket = serverSocket.accept();
//                    System.out.println("Connected " + socket);
//                    SocketLauncher<LanguageClient> launcher = new SocketLauncher<>(server, LanguageClient.class, socket);
//                    System.out.println("SocketLauncher " + launcher);
//                    // connect a remote chat client proxy to the chat server
//                    Runnable removeClient = server.addClient(launcher.getRemoteProxy());
//                    System.out.println("Runnable removeClient " + removeClient);
//                    // Start the listener for JsonRPC
//                    Future<?> startListening = launcher.startListening();
//                    System.out.println("startListening");
//                    // Get the computed result from LS.
//                    startListening.get();
//                }
//            });
//            System.out.println("Enter any character to stop");
//            System.in.read();
//            System.exit(0);
//        }

//        
//        try {
//            // Creating client socket
//            Socket clientSocket = new Socket(ADDRESS, PORT);
//            // start the language server
//            startServer(clientSocket.getInputStream(), clientSocket.getOutputStream());
//        } catch (IOException | InterruptedException | ExecutionException e) {
//            e.printStackTrace();
//        }
    }

    /**
     * Start the language server.
     * @param in System Standard input stream
     * @param out System standard output stream
     * @throws ExecutionException Unable to start the server
     * @throws InterruptedException Unable to start the server
     */
    private static void startServer(InputStream in, OutputStream out) throws ExecutionException, InterruptedException {
        System.out.println("startServer ");
        // Initialize the server
        DroolsLspServer server = new DroolsLspServer();
        System.out.println("DroolsLspServer " + server);
        // Create JSON RPC launcher for HelloLanguageServer instance.
        Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, in, out);
        System.out.println("Launcher<LanguageClient> " + launcher);

        // Connect the server
        server.connect(launcher.getRemoteProxy());
        System.out.println("Connected");

        // Start the listener for JsonRPC
        Future<?> startListening = launcher.startListening();
        System.out.println("Future<?> startListening " + startListening);

        // Get the computed result from LS.
        startListening.get();
    }
}
