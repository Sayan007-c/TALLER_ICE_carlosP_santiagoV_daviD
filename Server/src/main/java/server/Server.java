package server;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;

/**
 * Punto de entrada principal para el Servidor ZeroC Ice.
 * Cumple con el estándar de ejecución solicitado:
 * ./gradlew runServer
 */
public class Server {
    public static void main(String[] args) {
        System.out.println("================================================================");
        System.out.println("   UNIVERSIDAD ICESI - COMPUTACION EN INTERNET I");
        System.out.println("   SERVIDOR DISTRIBUIDO DE CHAT, ARCHIVOS Y VOZ (ZEROC ICE)");
        System.out.println("================================================================");

        // Inicialización segura del Communicator de Ice
        try (Communicator communicator = Util.initialize(args)) {
            // Creamos el adaptador de objetos escuchando en el puerto TCP 10000
            int port = 10000;
            String endpoint = "default -p " + port;
            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints("ChatServerAdapter", endpoint);

            // Instanciar el Servant del servidor
            ChatServerI serverImpl = new ChatServerI();

            // Registrar el servant en el Object Adapter con identidad "ChatServer"
            adapter.add(serverImpl, Util.stringToIdentity("ChatServer"));

            // Activar el adaptador para empezar a recibir invocaciones RPC
            adapter.activate();

            System.out.println("[INFO] Servidor Ice iniciado exitosamente.");
            System.out.println("[INFO] Escuchando peticiones en: " + endpoint);
            System.out.println("[INFO] Presione Ctrl+C en cualquier momento para apagar el servidor.");
            System.out.println("----------------------------------------------------------------");

            // Mantener el servidor activo hasta que se reciba señal de apagado
            communicator.waitForShutdown();
        } catch (Exception ex) {
            System.err.println("[ERROR CRITICO] Ocurrio un error al ejecutar el servidor: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
