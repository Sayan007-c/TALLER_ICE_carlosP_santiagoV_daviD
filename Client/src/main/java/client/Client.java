package client;

import ChatApp.*;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.ObjectPrx;
import com.zeroc.Ice.Util;

import java.util.Scanner;
import java.util.UUID;

/**
 * Cliente Interactivo para la plataforma distribuida de Chat y Voz.
 * Cumple con el estándar de ejecución solicitado:
 * ./gradlew runClient
 * 
 * Desarrollado para Computacion en Internet I (Universidad Icesi).
 * Integrante 1: Manejo de sesion, presencia, callbacks y mensajeria directa 1 a 1.
 */
public class Client {

    private static volatile boolean running = true;
    private static volatile boolean loggedIn = false;
    private static String currentNickname = "";
    private static ChatServerPrx server = null;

    public static void main(String[] args) {
        System.out.println("================================================================");
        System.out.println("   UNIVERSIDAD ICESI - COMPUTACION EN INTERNET I");
        System.out.println("   CLIENTE DE COMUNICACION DISTRIBUIDA (ZEROC ICE)");
        System.out.println("================================================================");

        String host = "localhost";
        int port = 10000;

        // Permitir pasar host o puerto opcionalmente por argumentos
        if (args.length >= 1) host = args[0];
        if (args.length >= 2) {
            try { port = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
        }

        try (Communicator communicator = Util.initialize(args)) {
            // 1. Obtener proxy del servidor Ice
            String serverProxyStr = "ChatServer:default -h " + host + " -p " + port;
            System.out.println("[INFO] Conectando al servidor Ice en " + host + ":" + port + "...");
            ObjectPrx baseProxy = communicator.stringToProxy(serverProxyStr);
            server = ChatServerPrx.checkedCast(baseProxy);

            if (server == null) {
                System.err.println("[ERROR] No se pudo obtener el proxy del servidor. Verifique que este activo.");
                return;
            }

            // 2. Crear ObjectAdapter local en el cliente para recibir callbacks del servidor
            ObjectAdapter callbackAdapter = communicator.createObjectAdapterWithEndpoints(
                "ClientCallbackAdapter", "default"
            );

            // 3. Registrar el ShutdownHook para desconexión limpia en caso de cierre forzado
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (loggedIn && server != null) {
                    try {
                        System.out.println("\n[SISTEMA] Cerrando sesion de forma segura...");
                        server.logout(currentNickname);
                    } catch (Exception ignored) {}
                }
            }));

            Scanner scanner = new Scanner(System.in);

            // 4. Proceso de autenticación / Login interactivo con nickname único (RF-01)
            while (!loggedIn && running) {
                System.out.print("\nIngrese su nickname para ingresar a la plataforma: ");
                String inputNick = scanner.nextLine().trim();

                if (inputNick.isEmpty()) {
                    System.out.println("\u001B[31m[ERROR] El nickname no puede estar en blanco.\u001B[0m");
                    continue;
                }

                try {
                    currentNickname = inputNick;

                    // Crear el servant del callback específico de este cliente
                    ClientCallbackI callbackImpl = new ClientCallbackI(currentNickname);
                    String identityName = "ClientCallback-" + UUID.randomUUID();
                    ObjectPrx cbBase = callbackAdapter.add(callbackImpl, Util.stringToIdentity(identityName));
                    callbackAdapter.activate();

                    ClientCallbackPrx callbackPrx = ClientCallbackPrx.uncheckedCast(cbBase);

                    // Invocación RPC de Login
                    server.login(currentNickname, callbackPrx);
                    loggedIn = true;
                    System.out.println("\u001B[32m[EXITO] Bienvenido @" + currentNickname + "! Sesion iniciada correctamente.\u001B[0m");
                } catch (NicknameInUseException ex) {
                    System.out.println("\u001B[31m[AVISO] " + ex.reason + " Intente con otro nombre.\u001B[0m");
                } catch (Exception ex) {
                    System.err.println("[ERROR] Error de comunicacion al intentar login: " + ex.getMessage());
                    return;
                }
            }

            // 5. Imprimir menú de ayuda inicial
            printHelpMenu();

            // 6. Bucle de lectura de comandos desacoplado para la CLI interactiva
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
            System.err.println("[ERROR CLIENTE] Error inesperado: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Procesa los comandos ingresados por el usuario en la CLI interactiva.
     */
    private static void handleCommand(String line) {
        String[] parts = line.split("\\s+", 3);
        String command = parts[0].toLowerCase();

        try {
            switch (command) {
                // RF-02: Mensajería privada directa 1 a 1
                case "/msg":
                    if (parts.length < 3) {
                        System.out.println("\u001B[33m[USO] /msg <destinatario> <mensaje>\u001B[0m");
                    } else {
                        String targetUser = parts[1];
                        String msgContent = parts[2];
                        server.sendPrivateMessage(currentNickname, targetUser, msgContent);
                        System.out.println("\u001B[32m-> Mensaje enviado a @" + targetUser + "\u001B[0m");
                    }
                    break;

                // RF-01: Consultar presencia / usuarios activos
                case "/users":
                    String[] users = server.getOnlineUsers();
                    System.out.println("\n========== USUARIOS EN LINEA (" + users.length + ") ==========");
                    for (String u : users) {
                        String marker = u.equalsIgnoreCase(currentNickname) ? " (Tu)" : "";
                        System.out.println(" • @" + u + marker);
                    }
                    System.out.println("==========================================");
                    break;

                // Comandos para Integrante 2 (Salas y Archivos)
                case "/create":
                    if (parts.length < 2) {
                        System.out.println("[USO] /create <nombre_sala>");
                    } else {
                        server.createRoom(parts[1], currentNickname);
                        System.out.println("\u001B[32m[SALA] Sala '" + parts[1] + "' creada exitosamente.\u001B[0m");
                    }
                    break;

                case "/join":
                    if (parts.length < 2) {
                        System.out.println("[USO] /join <nombre_sala>");
                    } else {
                        server.joinRoom(parts[1], currentNickname);
                        System.out.println("\u001B[32m[SALA] Te has unido a la sala '" + parts[1] + "'.\u001B[0m");
                    }
                    break;

                case "/leave":
                    if (parts.length < 2) {
                        System.out.println("[USO] /leave <nombre_sala>");
                    } else {
                        server.leaveRoom(parts[1], currentNickname);
                        System.out.println("\u001B[33m[SALA] Has salido de la sala '" + parts[1] + "'.\u001B[0m");
                    }
                    break;

                case "/rooms":
                    String[] rooms = server.listRooms();
                    System.out.println("\n========== SALAS ACTIVAS (" + rooms.length + ") ==========");
                    if (rooms.length == 0) {
                        System.out.println(" No hay salas creadas aun. Puedes crear una con /create <nombre>");
                    } else {
                        for (String r : rooms) System.out.println(" # " + r);
                    }
                    System.out.println("==========================================");
                    break;

                case "/roommsg":
                    if (parts.length < 3) {
                        System.out.println("[USO] /roommsg <sala> <mensaje>");
                    } else {
                        server.sendRoomMessage(parts[1], currentNickname, parts[2]);
                    }
                    break;

                case "/sendfile":
                    System.out.println("\u001B[33m[INFO] Comando asignado al Módulo de Transferencia Multimedia (Integrante 2).\u001B[0m");
                    break;

                // Comandos para Integrante 3 (Llamadas de Voz UDP)
                case "/call":
                case "/accept":
                case "/reject":
                case "/hangup":
                case "/mute":
                case "/unmute":
                case "/voicejoin":
                case "/voiceleave":
                    System.out.println("\u001B[33m[INFO] Comando asignado al Módulo de Llamadas de Voz UDP (Integrante 3).\u001B[0m");
                    break;

                case "/help":
                    printHelpMenu();
                    break;

                // RF-01: Logout ordenado y desconexión limpia
                case "/logout":
                case "/exit":
                    System.out.println("[SISTEMA] Cerrando sesion...");
                    if (loggedIn) {
                        server.logout(currentNickname);
                        loggedIn = false;
                    }
                    running = false;
                    System.out.println("[SISTEMA] Hasta pronto @" + currentNickname + "!");
                    System.exit(0);
                    break;

                default:
                    System.out.println("\u001B[31m[ERROR] Comando no reconocido: " + command + ". Escribe /help para ver la lista.\u001B[0m");
                    break;
            }
        } catch (ChatException ex) {
            System.out.println("\u001B[31m[ERROR DE APLICACION] " + ex.reason + "\u001B[0m");
        } catch (Exception ex) {
            System.err.println("[ERROR] No se pudo procesar el comando: " + ex.getMessage());
        }
    }

    private static void printHelpMenu() {
        System.out.println("\n----------------- COMANDOS DISPONIBLES -----------------");
        System.out.println(" \u001B[36mMensajeria y Presencia (Tuyo / Integrante 1):\u001B[0m");
        System.out.println("   /msg <usuario> <texto>       - Enviar mensaje privado 1 a 1");
        System.out.println("   /users                       - Ver usuarios conectados en tiempo real");
        System.out.println("   /logout o /exit              - Salir de la sesion limpiamente");
        System.out.println("   /help                        - Ver este menu de ayuda");
        System.out.println("\n \u001B[33mSalas y Archivos (Integrante 2):\u001B[0m");
        System.out.println("   /create <sala>               - Crear nueva sala de chat");
        System.out.println("   /join <sala>                 - Unirse a una sala");
        System.out.println("   /leave <sala>                - Salir de una sala");
        System.out.println("   /rooms                       - Listar salas activas");
        System.out.println("   /roommsg <sala> <texto>      - Enviar mensaje a toda la sala");
        System.out.println("   /sendfile <dest> <ruta>      - Enviar archivo multimedia en chunks");
        System.out.println("\n \u001B[35mLlamadas de Voz UDP (Integrante 3):\u001B[0m");
        System.out.println("   /call <usuario>              - Iniciar llamada de voz 1 a 1");
        System.out.println("   /accept                      - Aceptar llamada entrante");
        System.out.println("   /reject                      - Rechazar llamada entrante");
        System.out.println("   /hangup                      - Colgar llamada actual");
        System.out.println("   /mute / /unmute              - Silenciar / reactivar microfono");
        System.out.println("   /voicejoin <sala>            - Unirse a conferencia de voz de la sala");
        System.out.println("   /voiceleave                  - Salir de conferencia grupal");
        System.out.println("--------------------------------------------------------\n");
    }
}
