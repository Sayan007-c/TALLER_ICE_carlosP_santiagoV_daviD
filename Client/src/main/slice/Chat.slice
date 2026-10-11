module ChatApp
{
    exception ChatException
    {
        string reason;
    }

    exception NicknameInUseException extends ChatException
    {
    }

    exception UserNotFoundException extends ChatException
    {
    }

    exception RoomNotFoundException extends ChatException
    {
    }

    exception RoomAlreadyExistsException extends ChatException
    {
    }

    exception InvalidOperationException extends ChatException
    {
    }

    sequence<byte> ByteSeq;
    sequence<string> StringSeq;

    struct FileChunk
    {
        string fileId;
        string fileName;
        long totalSize;
        int chunkIndex;
        int totalChunks;
        ByteSeq data;
    }

    struct VoiceMember
    {
        string username;
        string ip;
        int udpPort;
    }
    sequence<VoiceMember> VoiceMemberList;

    interface ClientCallback
    {
        void onUserStatusChanged(string username, bool online);
        void onPrivateMessage(string fromUser, string message);
        void onRoomMessage(string roomName, string fromUser, string message);
        void onPrivateFileChunkReceived(string fromUser, FileChunk chunk);
        void onRoomFileChunkReceived(string roomName, string fromUser, FileChunk chunk);
        void onIncomingCall(string caller, string callerIp, int callerUdpPort);
        void onCallAccepted(string callee, string calleeIp, int calleeUdpPort);
        void onCallRejected(string callee, string reason);
        void onCallEnded(string remoteUser);
        void onUserJoinedVoiceGroup(string roomName, string username, string ip, int udpPort);
        void onUserLeftVoiceGroup(string roomName, string username);
    }

    interface ChatServer
    {
        void login(string username, ClientCallback* callback) throws NicknameInUseException;
        void logout(string username);
        StringSeq getOnlineUsers();

        void sendPrivateMessage(string fromUser, string toUser, string message) throws UserNotFoundException;

        void createRoom(string roomName, string username) throws RoomAlreadyExistsException;
        void joinRoom(string roomName, string username) throws RoomNotFoundException;
        void leaveRoom(string roomName, string username) throws RoomNotFoundException;
        StringSeq listRooms();
        void sendRoomMessage(string roomName, string fromUser, string message) throws RoomNotFoundException;

        void sendPrivateFileChunk(string fromUser, string toUser, FileChunk chunk) throws UserNotFoundException;
        void sendRoomFileChunk(string roomName, string fromUser, FileChunk chunk) throws RoomNotFoundException;

        void requestCall(string caller, string callee, string callerIp, int callerUdpPort) throws UserNotFoundException;
        void acceptCall(string callee, string caller, string calleeIp, int calleeUdpPort) throws UserNotFoundException;
        void rejectCall(string callee, string caller, string reason) throws UserNotFoundException;
        void endCall(string fromUser, string toUser);

        VoiceMemberList joinVoiceGroup(string roomName, string username, string ip, int udpPort) throws RoomNotFoundException;
        void leaveVoiceGroup(string roomName, string username) throws RoomNotFoundException;
        int getVoiceRelayPort(string roomName) throws RoomNotFoundException;
    }
}
