
package client;

<<<<<<< Updated upstream
public class Client {


=======
import ChatApp.*;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.ObjectPrx;
import com.zeroc.Ice.Util;

import java.util.Scanner;
import java.util.UUID;

public class Client {

    private static volatile boolean running = true;
    private static volatile boolean loggedIn = false;

    private static String currentNickname = "";

    private static ChatServerPrx server;
    private static volatile VoiceController voice;

    public static void main(String[] args) {

        System.out.println(
                "=======================================");
        System.out.println(
                "UNIVERSIDAD ICESI - COMPUTACION INTERNET");
        System.out.println(
                "CHAT DISTRIBUIDO CON ZEROC ICE");
        System.out.println(
                "=======================================");

        String host = "localhost";
        int port = 10000;

        if (args.length >= 1) {
            host = args[0];
        }

        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
            }
        }

        try (Communicator communicator = Util.initialize(args)) {

            String serverProxyStr =
                    "ChatServer:default -h "
                            + host + " -p " + port;

            ObjectPrx baseProxy =
                    communicator.stringToProxy(serverProxyStr);

            server = ChatServerPrx.checkedCast(baseProxy);

            if (server == null) {
                System.err.println(
                        "[ERROR] Servidor no disponible");
                return;
            }

            ObjectAdapter callbackAdapter =
                    communicator.createObjectAdapterWithEndpoints(
                            "ClientCallbackAdapter",
                            "default -h 0.0.0.0 -p 0");

            Runtime.getRuntime().addShutdownHook(
                    new Thread(() -> {

                        if (voice != null) {
                            try {
                                voice.close();
                            } catch (Exception ignored) {
                            }
                        }

                        if (loggedIn && server != null) {
                            try {
                                server.logout(currentNickname);
                            } catch (Exception ignored) {
                            }
                        }
                    }));

            Scanner scanner = new Scanner(System.in);

            // RF-01: Login.
            while (!loggedIn && running) {

                System.out.print("Ingrese nickname: ");
                String inputNick = scanner.nextLine().trim();

                if (inputNick.isEmpty()) {
                    System.out.println(
                            "[ERROR] Nickname vacio");
                    continue;
                }

                try {
                    currentNickname = inputNick;

                    voice = new VoiceController(
                            server, currentNickname);

                    ClientCallbackI callbackImpl =
                            new ClientCallbackI(
                                    currentNickname, voice);

                    String identityName =
                            "ClientCallback-"
                                    + UUID.randomUUID();

                    ObjectPrx cbBase = callbackAdapter.add(
                            callbackImpl,
                            Util.stringToIdentity(identityName));

                    callbackAdapter.activate();

                    ClientCallbackPrx callbackPrx =
                            ClientCallbackPrx.uncheckedCast(cbBase);

                    server.login(
                            currentNickname, callbackPrx);

                    loggedIn = true;

                    System.out.println(
                            "[EXITO] Bienvenido @"
                                    + currentNickname);

                } catch (NicknameInUseException ex) {

                    if (voice != null) {
                        voice.close();
                        voice = null;
                    }

                    System.out.println(
                            "[ERROR] " + ex.reason);

                } catch (Exception ex) {

                    System.err.println(
                            "[ERROR LOGIN] " + ex.getMessage());
                    return;
                }
            }

            printHelpMenu();

            System.out.print("> ");

            while (running && scanner.hasNextLine()) {

                String line = scanner.nextLine().trim();

                if (line.isEmpty()) {
                    System.out.print("> ");
                    continue;
                }

                handleCommand(line);

                if (running) {
                    System.out.print("> ");
                }
            }

        } catch (Exception ex) {

            System.err.println(
                    "[ERROR CLIENTE] " + ex.getMessage());

            ex.printStackTrace();
        }
    }

    private static void handleCommand(String line) {

        String[] parts = line.split("\\s+", 3);
        String command = parts[0].toLowerCase();

        try {

            switch (command) {

                // RF-02: Mensaje privado.
                case "/msg":

                    if (parts.length < 3) {
                        System.out.println(
                                "[USO] /msg <usuario> <mensaje>");
                    } else {

                        server.sendPrivateMessage(
                                currentNickname,
                                parts[1],
                                parts[2]);

                        System.out.println(
                                "[MSG] Mensaje enviado");
                    }
                    break;

                // RF-01: Usuarios conectados.
                case "/users":

                    String[] users =
                            server.getOnlineUsers();

                    System.out.println(
                            "=== USUARIOS EN LINEA ===");

                    for (String user : users) {
                        System.out.println("@" + user);
                    }
                    break;

                // RF-03: Crear sala.
                case "/create":

                    if (parts.length < 2) {
                        System.out.println(
                                "[USO] /create <sala>");
                    } else {

                        server.createRoom(
                                parts[1], currentNickname);

                        System.out.println(
                                "[SALA] Creada: " + parts[1]);
                    }
                    break;

                // RF-03: Entrar a sala.
                case "/join":

                    if (parts.length < 2) {
                        System.out.println(
                                "[USO] /join <sala>");
                    } else {

                        server.joinRoom(
                                parts[1], currentNickname);

                        System.out.println(
                                "[SALA] Unido a " + parts[1]);
                    }
                    break;

                // RF-03: Salir de sala.
                case "/leave":

                    if (parts.length < 2) {
                        System.out.println(
                                "[USO] /leave <sala>");
                    } else {

                        server.leaveRoom(
                                parts[1], currentNickname);
                    }
                    break;

                // RF-03: Listar salas.
                case "/rooms":

                    String[] rooms = server.listRooms();

                    System.out.println(
                            "=== SALAS ACTIVAS ===");

                    for (String room : rooms) {
                        System.out.println("#" + room);
                    }
                    break;

                // RF-03: Mensaje grupal.
                case "/roommsg":

                    if (parts.length < 3) {
                        System.out.println(
                                "[USO] /roommsg <sala> <mensaje>");
                    } else {

                        server.sendRoomMessage(
                                parts[1],
                                currentNickname,
                                parts[2]);
                    }
                    break;

                // RF-04: Archivos.
                case "/sendfile":

                    // Pendiente de integracion con
                    // el modulo del Integrante 2.
                    System.out.println(
                            "[INFO] Transferencia de archivos "
                                    + "pendiente de integracion");
                    break;

                // RF-05: Iniciar llamada individual.
                case "/call":

                    if (parts.length < 2) {
                        System.out.println(
                                "[USO] /call <usuario>");
                    } else {
                        voice.call(parts[1]);
                    }
                    break;

                // RF-05: Aceptar llamada.
                case "/accept":
                    voice.accept();
                    break;

                // RF-05: Rechazar llamada.
                case "/reject":
                    voice.reject();
                    break;

                // RF-05: Finalizar llamada.
                case "/hangup":
                    voice.hangup();
                    break;

                // RF-06: Silenciar micrófono.
                case "/mute":
                    voice.mute(true);
                    break;

                // RF-06: Activar micrófono.
                case "/unmute":
                    voice.mute(false);
                    break;

                // RF-06: Entrar a conferencia.
                case "/voicejoin":

                    if (parts.length < 2) {
                        System.out.println(
                                "[USO] /voicejoin <sala>");
                    } else {
                        voice.joinRoom(parts[1]);
                    }
                    break;

                // RF-06: Salir de conferencia.
                case "/voiceleave":
                    voice.leaveRoom();
                    break;

                // Mostrar comandos.
                case "/help":
                    printHelpMenu();
                    break;

                // Desconectar.
                case "/logout":
                case "/exit":

                    if (voice != null) {
                        voice.close();
                        voice = null;
                    }

                    if (loggedIn) {
                        server.logout(currentNickname);
                        loggedIn = false;
                    }

                    running = false;

                    System.out.println(
                            "[SISTEMA] Sesion finalizada");
                    break;

                default:
                    System.out.println(
                            "[ERROR] Comando no reconocido");
                    break;
            }

        } catch (ChatException ex) {

            System.out.println(
                    "[ERROR] " + ex.reason);

        } catch (Exception ex) {

            System.err.println(
                    "[ERROR] " + ex.getMessage());
        }
    }

    private static void printHelpMenu() {

        System.out.println(
                "\n========== COMANDOS ==========");

        System.out.println(
                "/msg <usuario> <mensaje>");
        System.out.println("/users");

        System.out.println("/create <sala>");
        System.out.println("/join <sala>");
        System.out.println("/leave <sala>");
        System.out.println("/rooms");
        System.out.println("/roommsg <sala> <mensaje>");
        System.out.println("/sendfile <dest> <ruta>");

        System.out.println("/call <usuario>");
        System.out.println("/accept");
        System.out.println("/reject");
        System.out.println("/hangup");
        System.out.println("/mute");
        System.out.println("/unmute");
        System.out.println("/voicejoin <sala>");
        System.out.println("/voiceleave");

        System.out.println("/help");
        System.out.println("/logout");
        System.out.println("/exit");

        System.out.println(
                "==============================\n");
    }
>>>>>>> Stashed changes
}

