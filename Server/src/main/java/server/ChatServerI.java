package server;

import ChatApp.*;
import com.zeroc.Ice.Current;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementación del Servant del servidor de chat en ZeroC Ice.
 * Desarrollado para la Unidad 2 de Computación en Internet I (Universidad Icesi).
 * 
 * Responsabilidad de Integrante 1:
 * - RF-01: Gestión de Sesión, Presencia y Registro de Clientes (Login, Logout, callbacks, detección de fallos)
 * - RF-02: Mensajería Privada Directa (1 a 1 con control de errores)
 * - Estructura concurrente base (Thread-Safety con ConcurrentHashMap)
 */
public class ChatServerI implements ChatServer {

    // Mapa de usuarios activos y sus proxies de callback (Thread-Safe para concurrencia en Ice)
    private final Map<String, ClientCallbackPrx> activeUsers = new ConcurrentHashMap<>();

    // Mapas para que los compañeros implementen salas y llamadas de voz
    // (Declarados aquí para que todo el equipo mantenga la misma estructura unificada)
    private final Map<String, Map<String, ClientCallbackPrx>> chatRooms = new ConcurrentHashMap<>();
    private final Map<String, Map<String, VoiceMember>> voiceRooms = new ConcurrentHashMap<>();

    // =========================================================================
    // MODULO 1 & 2: SESIÓN, PRESENCIA Y MENSAJERÍA PRIVADA (INTEGRANTE 1)
    // =========================================================================

    @Override
    public void login(String username, ClientCallbackPrx callback, Current current) throws NicknameInUseException {
        if (username == null || username.trim().isEmpty()) {
            throw new NicknameInUseException("El nickname no puede estar vacío.");
        }

        String user = username.trim();

        // RF-01: Rechazar conexión si el nickname ya existe
        if (activeUsers.containsKey(user)) {
            System.out.println("[RECHAZADO] Intento de login duplicado con nickname: " + user);
            throw new NicknameInUseException("El nickname '" + user + "' ya se encuentra en uso por otra sesión activa.");
        }

        // Registrar el proxy de callback del cliente
        activeUsers.put(user, callback);
        System.out.println("[LOGIN] Usuario autenticado exitosamente: @" + user + " (Total conectados: " + activeUsers.size() + ")");

        // RF-01: Notificar en tiempo real a los demás clientes conectados que este usuario entró
        notifyUserPresence(user, true);
    }

    @Override
    public void logout(String username, Current current) {
        if (username == null) return;
        String user = username.trim();

        ClientCallbackPrx removed = activeUsers.remove(user);
        if (removed != null) {
            System.out.println("[LOGOUT] Usuario desconectado limpiamente: @" + user + " (Restantes: " + activeUsers.size() + ")");
            // RF-01: Notificar a todos los demás clientes que el usuario se desconectó
            notifyUserPresence(user, false);
        }
    }

    @Override
    public String[] getOnlineUsers(Current current) {
        return activeUsers.keySet().toArray(new String[0]);
    }

    @Override
    public void sendPrivateMessage(String fromUser, String toUser, String message, Current current) throws UserNotFoundException {
        if (toUser == null || !activeUsers.containsKey(toUser.trim())) {
            throw new UserNotFoundException("El destinatario '" + toUser + "' no existe o se encuentra desconectado.");
        }

        ClientCallbackPrx targetCallback = activeUsers.get(toUser.trim());
        try {
            // RF-02: Despacho asíncrono al callback del destinatario
            targetCallback.onPrivateMessage(fromUser, message);
            System.out.println("[MSG DIRECTO] @" + fromUser + " -> @" + toUser + ": " + message);
        } catch (Exception ex) {
            // Tolerancia a fallos: Si el cliente cayó abruptamente, limpiar su registro
            System.err.println("[FALLO CONEXION] No se pudo entregar mensaje a @" + toUser + ". Removiendo de sesión.");
            activeUsers.remove(toUser.trim());
            notifyUserPresence(toUser.trim(), false);
            throw new UserNotFoundException("El destinatario '" + toUser + "' perdió la conexión de red.");
        }
    }

    /**
     * Notifica a todos los clientes activos sobre cambios en el estado de presencia de un usuario.
     */
    private void notifyUserPresence(String username, boolean online) {
        for (Map.Entry<String, ClientCallbackPrx> entry : activeUsers.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase(username)) {
                try {
                    entry.getValue().onUserStatusChanged(username, online);
                } catch (Exception ex) {
                    // Limpieza segura en caso de que algún proxy falle
                    activeUsers.remove(entry.getKey());
                }
            }
        }
    }

    // =========================================================================
    // MODULO 3 & 4: SALAS GRUPALES Y CONTENIDO MULTIMEDIA (INTEGRANTE 2)
    // =========================================================================

    @Override
    public void createRoom(String roomName, String username, Current current) throws RoomAlreadyExistsException {
        // TODO (Integrante 2): Implementar creación de salas y validación de duplicados
        if (chatRooms.containsKey(roomName)) {
            throw new RoomAlreadyExistsException("La sala '" + roomName + "' ya existe.");
        }
        chatRooms.put(roomName, new ConcurrentHashMap<>());
        System.out.println("[SALA CREADA] Sala '" + roomName + "' creada por @" + username);
    }

    @Override
    public void joinRoom(String roomName, String username, Current current) throws RoomNotFoundException {
        // TODO (Integrante 2): Implementar suscripción de usuarios a la sala
        Map<String, ClientCallbackPrx> room = chatRooms.get(roomName);
        if (room == null) {
            throw new RoomNotFoundException("La sala '" + roomName + "' no existe.");
        }
        ClientCallbackPrx cb = activeUsers.get(username);
        if (cb != null) {
            room.put(username, cb);
            System.out.println("[SALA JOIN] @" + username + " se unió a la sala '" + roomName + "'");
        }
    }

    @Override
    public void leaveRoom(String roomName, String username, Current current) throws RoomNotFoundException {
        // TODO (Integrante 2): Implementar salida ordenada de la sala
        Map<String, ClientCallbackPrx> room = chatRooms.get(roomName);
        if (room == null) {
            throw new RoomNotFoundException("La sala '" + roomName + "' no existe.");
        }
        room.remove(username);
        System.out.println("[SALA LEAVE] @" + username + " salió de la sala '" + roomName + "'");
    }

    @Override
    public String[] listRooms(Current current) {
        // TODO (Integrante 2): Retornar catálogo de salas activas
        return chatRooms.keySet().toArray(new String[0]);
    }

    @Override
    public void sendRoomMessage(String roomName, String fromUser, String message, Current current) throws RoomNotFoundException {
        // TODO (Integrante 2): Implementar difusión (broadcast) aislando a la sala y omitiendo al emisor
        Map<String, ClientCallbackPrx> room = chatRooms.get(roomName);
        if (room == null) {
            throw new RoomNotFoundException("La sala '" + roomName + "' no existe.");
        }
        for (Map.Entry<String, ClientCallbackPrx> member : room.entrySet()) {
            if (!member.getKey().equalsIgnoreCase(fromUser)) {
                try {
                    member.getValue().onRoomMessage(roomName, fromUser, message);
                } catch (Exception e) {
                    room.remove(member.getKey());
                }
            }
        }
    }

    @Override
    public void sendPrivateFileChunk(String fromUser, String toUser, FileChunk chunk, Current current) throws UserNotFoundException {
        // TODO (Integrante 2): Transferencia de chunk multimedia a un usuario directo
        ClientCallbackPrx target = activeUsers.get(toUser);
        if (target == null) {
            throw new UserNotFoundException("El destinatario '" + toUser + "' no está disponible.");
        }
        target.onPrivateFileChunkReceived(fromUser, chunk);
    }

    @Override
    public void sendRoomFileChunk(String roomName, String fromUser, FileChunk chunk, Current current) throws RoomNotFoundException {
        // TODO (Integrante 2): Difusión de chunk multimedia a todos los miembros de la sala
        Map<String, ClientCallbackPrx> room = chatRooms.get(roomName);
        if (room == null) {
            throw new RoomNotFoundException("La sala '" + roomName + "' no existe.");
        }
        for (Map.Entry<String, ClientCallbackPrx> member : room.entrySet()) {
            if (!member.getKey().equalsIgnoreCase(fromUser)) {
                try {
                    member.getValue().onRoomFileChunkReceived(roomName, fromUser, chunk);
                } catch (Exception e) {
                    room.remove(member.getKey());
                }
            }
        }
    }

    // =========================================================================
    // MODULO 5 & 6: LLAMADAS DE VOZ UDP DIRECTAS Y GRUPALES (INTEGRANTE 3)
    // =========================================================================

    @Override
    public void requestCall(String caller, String callee, String callerIp, int callerUdpPort, Current current) throws UserNotFoundException {
        // TODO (Integrante 3): Señalización Ice para timbrar y solicitar llamada directa
        ClientCallbackPrx target = activeUsers.get(callee);
        if (target == null) {
            throw new UserNotFoundException("El usuario '" + callee + "' no está disponible para llamada.");
        }
        target.onIncomingCall(caller, callerIp, callerUdpPort);
    }

    @Override
    public void acceptCall(String callee, String caller, String calleeIp, int calleeUdpPort, Current current) throws UserNotFoundException {
        // TODO (Integrante 3): Señalización Ice para aceptar llamada reportando IP/puerto UDP
        ClientCallbackPrx target = activeUsers.get(caller);
        if (target == null) {
            throw new UserNotFoundException("El llamador ya no está conectado.");
        }
        target.onCallAccepted(callee, calleeIp, calleeUdpPort);
    }

    @Override
    public void rejectCall(String callee, String caller, String reason, Current current) throws UserNotFoundException {
        // TODO (Integrante 3): Señalización Ice para rechazar llamada
        ClientCallbackPrx target = activeUsers.get(caller);
        if (target != null) {
            target.onCallRejected(callee, reason);
        }
    }

    @Override
    public void endCall(String fromUser, String toUser, Current current) {
        // TODO (Integrante 3): Notificar cuelgue de llamada y liberación de sockets
        ClientCallbackPrx target = activeUsers.get(toUser);
        if (target != null) {
            try {
                target.onCallEnded(fromUser);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public VoiceMember[] joinVoiceGroup(String roomName, String username, String ip, int udpPort, Current current) throws RoomNotFoundException {
        // TODO (Integrante 3): Agregar usuario a conferencia de audio de la sala y retornar miembros
        Map<String, VoiceMember> members = voiceRooms.computeIfAbsent(roomName, k -> new ConcurrentHashMap<>());
        VoiceMember newMember = new VoiceMember(username, ip, udpPort);
        members.put(username, newMember);

        // Notificar a los otros miembros del grupo de voz
        Map<String, ClientCallbackPrx> room = chatRooms.get(roomName);
        if (room != null) {
            for (Map.Entry<String, ClientCallbackPrx> entry : room.entrySet()) {
                if (!entry.getKey().equalsIgnoreCase(username)) {
                    try {
                        entry.getValue().onUserJoinedVoiceGroup(roomName, username, ip, udpPort);
                    } catch (Exception ignored) {}
                }
            }
        }
        return members.values().toArray(new VoiceMember[0]);
    }

    @Override
    public void leaveVoiceGroup(String roomName, String username, Current current) throws RoomNotFoundException {
        // TODO (Integrante 3): Remover de conferencia grupal de audio
        Map<String, VoiceMember> members = voiceRooms.get(roomName);
        if (members != null) {
            members.remove(username);
        }
        Map<String, ClientCallbackPrx> room = chatRooms.get(roomName);
        if (room != null) {
            for (Map.Entry<String, ClientCallbackPrx> entry : room.entrySet()) {
                if (!entry.getKey().equalsIgnoreCase(username)) {
                    try {
                        entry.getValue().onUserLeftVoiceGroup(roomName, username);
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    @Override
    public int getVoiceRelayPort(String roomName, Current current) throws RoomNotFoundException {
        // TODO (Integrante 3): Retornar puerto UDP de Relay centralizado si se usa arquitectura SFU
        return 0;
    }
}
