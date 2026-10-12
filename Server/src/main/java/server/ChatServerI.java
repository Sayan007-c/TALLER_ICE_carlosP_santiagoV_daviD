
package server;

import ChatApp.*;
import com.zeroc.Ice.Current;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServerI implements ChatServer {

    private final Map<String, ClientCallbackPrx> activeUsers =
            new ConcurrentHashMap<>();

    private final Map<String, Map<String, ClientCallbackPrx>> chatRooms =
            new ConcurrentHashMap<>();

    private final Map<String, Map<String, VoiceMember>> voiceRooms =
            new ConcurrentHashMap<>();

    // RF-01: Registrar usuario.
    @Override
    public void login(
            String username,
            ClientCallbackPrx callback,
            Current current) throws NicknameInUseException {

        if (username == null || username.trim().isEmpty()) {
            throw new NicknameInUseException(
                    "El nickname no puede estar vacio");
        }

        String user = username.trim();

        if (activeUsers.putIfAbsent(user, callback) != null) {
            throw new NicknameInUseException(
                    "El nickname ya esta en uso");
        }

        System.out.println(
                "[LOGIN] Usuario: @" + user);

        notifyUserPresence(user, true);
    }

    // RF-01: Cerrar sesion.
    @Override
    public void logout(String username, Current current) {

        if (username == null) return;

        String user = username.trim();

        ClientCallbackPrx removed = activeUsers.remove(user);

        if (removed == null) return;

        for (Map.Entry<String, Map<String, VoiceMember>> entry
                : voiceRooms.entrySet()) {

            Map<String, VoiceMember> members =
                    entry.getValue();

            if (members.remove(user) != null) {

                for (VoiceMember member : members.values()) {

                    ClientCallbackPrx cb =
                            activeUsers.get(member.username);

                    if (cb != null) {
                        try {
                            cb.onUserLeftVoiceGroup(
                                    entry.getKey(), user);
                        } catch (Exception ignored) {
                        }
                    }
                }

                if (members.isEmpty()) {
                    voiceRooms.remove(
                            entry.getKey(), members);
                }
            }
        }

        // Retirar al usuario de las salas.
        for (Map<String, ClientCallbackPrx> room
                : chatRooms.values()) {

            room.remove(user);
        }

        System.out.println(
                "[LOGOUT] Usuario: @" + user);

        notifyUserPresence(user, false);
    }

    // RF-01: Obtener usuarios conectados.
    @Override
    public String[] getOnlineUsers(Current current) {

        return activeUsers.keySet()
                .toArray(new String[0]);
    }

    // RF-02: Mensaje privado.
    @Override
    public void sendPrivateMessage(
            String fromUser,
            String toUser,
            String message,
            Current current) throws UserNotFoundException {

        ClientCallbackPrx target =
                activeUsers.get(toUser);

        if (target == null) {
            throw new UserNotFoundException(
                    "Destinatario desconectado");
        }

        try {
            target.onPrivateMessage(
                    fromUser, message);

            System.out.println(
                    "[MENSAJE] @" + fromUser
                            + " -> @" + toUser);

        } catch (Exception e) {

            activeUsers.remove(toUser);

            notifyUserPresence(toUser, false);

            throw new UserNotFoundException(
                    "No se pudo entregar el mensaje");
        }
    }

    private void notifyUserPresence(
            String username, boolean online) {

        for (Map.Entry<String, ClientCallbackPrx> entry
                : activeUsers.entrySet()) {

            if (entry.getKey().equals(username)) {
                continue;
            }

            try {
                entry.getValue().onUserStatusChanged(
                        username, online);

            } catch (Exception e) {
                activeUsers.remove(
                        entry.getKey(), entry.getValue());
            }
        }
    }

    // RF-03: Crear sala.
    @Override
    public void createRoom(
            String roomName,
            String username,
            Current current) throws RoomAlreadyExistsException {

        Map<String, ClientCallbackPrx> newRoom =
                new ConcurrentHashMap<>();

        if (chatRooms.putIfAbsent(roomName, newRoom) != null) {
            throw new RoomAlreadyExistsException(
                    "La sala ya existe");
        }

        ClientCallbackPrx creator =
                activeUsers.get(username);

        if (creator != null) {
            newRoom.put(username, creator);
        }

        System.out.println(
                "[SALA] Creada: " + roomName);
    }

    // RF-03: Unirse a sala.
    @Override
    public void joinRoom(
            String roomName,
            String username,
            Current current) throws RoomNotFoundException {

        Map<String, ClientCallbackPrx> room =
                chatRooms.get(roomName);

        if (room == null) {
            throw new RoomNotFoundException(
                    "La sala no existe");
        }

        ClientCallbackPrx callback =
                activeUsers.get(username);

        if (callback != null) {
            room.put(username, callback);
        }

        System.out.println(
                "[SALA] @" + username
                        + " entro a " + roomName);
    }

    // RF-03: Salir de sala.
    @Override
    public void leaveRoom(
            String roomName,
            String username,
            Current current) throws RoomNotFoundException {

        Map<String, ClientCallbackPrx> room =
                chatRooms.get(roomName);

        if (room == null) {
            throw new RoomNotFoundException(
                    "La sala no existe");
        }

        room.remove(username);

        // Retirar también de conferencia de voz.
        Map<String, VoiceMember> members =
                voiceRooms.get(roomName);

        if (members != null
                && members.remove(username) != null) {

            notifyVoiceDeparture(
                    roomName, username, members);

            if (members.isEmpty()) {
                voiceRooms.remove(roomName, members);
            }
        }

        System.out.println(
                "[SALA] @" + username
                        + " salio de " + roomName);
    }

    // RF-03: Listar salas.
    @Override
    public String[] listRooms(Current current) {

        return chatRooms.keySet()
                .toArray(new String[0]);
    }

    // RF-03: Mensajeria grupal.
    @Override
    public void sendRoomMessage(
            String roomName,
            String fromUser,
            String message,
            Current current) throws RoomNotFoundException {

        Map<String, ClientCallbackPrx> room =
                chatRooms.get(roomName);

        if (room == null) {
            throw new RoomNotFoundException(
                    "Sala no encontrada");
        }

        if (!room.containsKey(fromUser)) {
            throw new RoomNotFoundException(
                    "No perteneces a la sala");
        }

        for (Map.Entry<String, ClientCallbackPrx> member
                : room.entrySet()) {

            if (!member.getKey().equals(fromUser)) {

                try {
                    member.getValue().onRoomMessage(
                            roomName, fromUser, message);

                } catch (Exception e) {
                    room.remove(member.getKey());
                }
            }
        }
    }

    // RF-04: Enviar fragmento privado.
    @Override
    public void sendPrivateFileChunk(
            String fromUser,
            String toUser,
            FileChunk chunk,
            Current current) throws UserNotFoundException {

        ClientCallbackPrx target =
                activeUsers.get(toUser);

        if (target == null) {
            throw new UserNotFoundException(
                    "Destinatario no disponible");
        }

        try {
            target.onPrivateFileChunkReceived(
                    fromUser, chunk);
        } catch (Exception e) {
            activeUsers.remove(toUser);
            notifyUserPresence(toUser, false);
            throw new UserNotFoundException(
                    "El destinatario se desconecto durante la transferencia");
        }
    }

    // RF-04: Enviar fragmento a sala.
    @Override
    public void sendRoomFileChunk(
            String roomName,
            String fromUser,
            FileChunk chunk,
            Current current) throws RoomNotFoundException {

        Map<String, ClientCallbackPrx> room =
                chatRooms.get(roomName);

        if (room == null) {
            throw new RoomNotFoundException(
                    "Sala no encontrada");
        }

        if (!room.containsKey(fromUser)) {
            throw new RoomNotFoundException(
                    "No perteneces a la sala");
        }

        for (Map.Entry<String, ClientCallbackPrx> member
                : room.entrySet()) {

            if (!member.getKey().equals(fromUser)) {

                try {
                    member.getValue().onRoomFileChunkReceived(
                            roomName, fromUser, chunk);

                } catch (Exception e) {
                    room.remove(member.getKey());
                }
            }
        }
    }

    // RF-05: Solicitar llamada individual.
    @Override
    public void requestCall(
            String caller,
            String callee,
            String callerIp,
            int callerUdpPort,
            Current current) throws UserNotFoundException {

        ClientCallbackPrx target =
                activeUsers.get(callee);

        if (target == null
                || !activeUsers.containsKey(caller)) {

            throw new UserNotFoundException(
                    "Usuario no disponible");
        }

        target.onIncomingCall(
                caller, callerIp, callerUdpPort);

        System.out.println(
                "[LLAMADA] @" + caller
                        + " llama a @" + callee);
    }

    // RF-05: Aceptar llamada.
    @Override
    public void acceptCall(
            String callee,
            String caller,
            String calleeIp,
            int calleeUdpPort,
            Current current) throws UserNotFoundException {

        ClientCallbackPrx target =
                activeUsers.get(caller);

        if (target == null) {
            throw new UserNotFoundException(
                    "El llamador no esta conectado");
        }

        target.onCallAccepted(
                callee, calleeIp, calleeUdpPort);

        System.out.println(
                "[LLAMADA] @" + callee
                        + " acepto a @" + caller);
    }

    // RF-05: Rechazar llamada.
    @Override
    public void rejectCall(
            String callee,
            String caller,
            String reason,
            Current current) throws UserNotFoundException {

        ClientCallbackPrx target =
                activeUsers.get(caller);

        if (target != null) {
            target.onCallRejected(
                    callee, reason);
        }

        System.out.println(
                "[LLAMADA] Rechazada por @" + callee);
    }

    // RF-05: Terminar llamada.
    @Override
    public void endCall(
            String fromUser,
            String toUser,
            Current current) {

        ClientCallbackPrx target =
                activeUsers.get(toUser);

        if (target != null) {

            try {
                target.onCallEnded(fromUser);

            } catch (Exception e) {
                System.err.println(
                        "[LLAMADA] Fallo al notificar");
            }
        }

        System.out.println(
                "[LLAMADA] Finalizada: "
                        + fromUser + " -> " + toUser);
    }

    // RF-06: Unirse a conferencia UDP.
    @Override
    public VoiceMember[] joinVoiceGroup(
            String roomName,
            String username,
            String ip,
            int udpPort,
            Current current) throws RoomNotFoundException {

        Map<String, ClientCallbackPrx> room =
                chatRooms.get(roomName);

        if (room == null
                || !room.containsKey(username)) {

            throw new RoomNotFoundException(
                    "Debes pertenecer a una sala existente");
        }

        if (!activeUsers.containsKey(username)
                || ip == null
                || ip.isBlank()
                || udpPort < 1
                || udpPort > 65535) {

            throw new RoomNotFoundException(
                    "Usuario o puerto UDP invalido");
        }

        Map<String, VoiceMember> members =
                voiceRooms.computeIfAbsent(
                        roomName,
                        k -> new ConcurrentHashMap<>());

        VoiceMember[] previous =
                members.values()
                        .toArray(new VoiceMember[0]);

        members.put(
                username,
                new VoiceMember(
                        username, ip, udpPort));

        // Notificar nuevos participantes.
        for (VoiceMember member : previous) {

            if (member.username.equals(username)) {
                continue;
            }

            ClientCallbackPrx callback =
                    activeUsers.get(member.username);

            if (callback != null) {

                try {
                    callback.onUserJoinedVoiceGroup(
                            roomName,
                            username,
                            ip,
                            udpPort);

                } catch (Exception e) {
                    System.err.println(
                            "[VOZ] Error notificando participante");
                }
            }
        }

        System.out.println(
                "[VOZ GRUPAL] @" + username
                        + " entro a #" + roomName);

        return previous;
    }

    // RF-06: Abandonar conferencia.
    @Override
    public void leaveVoiceGroup(
            String roomName,
            String username,
            Current current) throws RoomNotFoundException {

        Map<String, VoiceMember> members =
                voiceRooms.get(roomName);

        if (members == null
                || members.remove(username) == null) {

            throw new RoomNotFoundException(
                    "No perteneces a la conferencia");
        }

        notifyVoiceDeparture(
                roomName, username, members);

        if (members.isEmpty()) {
            voiceRooms.remove(roomName, members);
        }

        System.out.println(
                "[VOZ GRUPAL] @" + username
                        + " salio de #" + roomName);
    }

    // Notificar salida de conferencia.
    private void notifyVoiceDeparture(
            String roomName,
            String username,
            Map<String, VoiceMember> members) {

        for (VoiceMember member : members.values()) {

            ClientCallbackPrx callback =
                    activeUsers.get(member.username);

            if (callback != null) {

                try {
                    callback.onUserLeftVoiceGroup(
                            roomName, username);

                } catch (Exception e) {
                    System.err.println(
                            "[VOZ] Error notificando salida");
                }
            }
        }
    }

    // Arquitectura P2P: no utiliza relay central.
    @Override
    public int getVoiceRelayPort(
            String roomName,
            Current current) throws RoomNotFoundException {

        return 0;
    }
}

